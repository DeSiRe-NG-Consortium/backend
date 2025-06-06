/*
 * Copyright 2023–2025 Nuromedia GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.desire.services;

import com.desire.dtos.PostAGVMeasurementRequest;
import com.desire.dtos.PostAGVMeasurementRequest.PostAGVMeasurementValueRequest;
import com.desire.dtos.PostAGVPositionRequest;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Coordinates;
import com.desire.model.repositories.AGVMeasurementRepository;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FakePositionService {

  private final @NonNull AGVMeasurementRepository agvMeasurementDao;
  private final @NonNull AGVMeasurementService agvMeasurementService;
  private final @NonNull AGVPositionService agvPositionService;

  private final Map<Campaigns, FakeState> campaigns = new HashMap<>();

  /**
   * In meters.
   */
  private static final Coordinates AGV_AREA_MIN = new Coordinates(0f, 0f, 0f);

  /**
   * In meters.
   */
  private static final Coordinates AGV_AREA_MAX = new Coordinates(10f, 10f, 0f);

  /**
   * In meters per second.
   */
  private static final float AGV_SPEED = 1f;

  private static final float DATA_RATE_MIN = 60f;

  private static final float DATA_RATE_MAX = 100f;

  private static final float LATENCY_MIN = 1f;

  private static final float LATENCY_MAX = 100f;

  public void start(Campaigns campaign, boolean generateFakePositions,
      boolean generateFakeMeasurements) {
    campaigns.put(campaign, new FakeState(generateFakePositions, generateFakeMeasurements));

    Optional<AGVMeasurements> measurementOptional = agvMeasurementDao.findFirstByCampaignIdOrderByMeasurementIdDesc(
        campaign.getId());

    measurementOptional.ifPresent(agvMeasurements -> campaigns.get(campaign).getMeasurementId()
        .set(agvMeasurements.getMeasurementId()));
  }

  public void stop(Campaigns campaign) {
    campaigns.keySet().stream().filter(c -> c.getId().equals(campaign.getId())).findFirst()
        .ifPresent(campaigns::remove);
  }

  @Scheduled(fixedRate = 1L, timeUnit = TimeUnit.SECONDS)
  public void execute() {
    for (Map.Entry<Campaigns, FakeState> campaignEntry : campaigns.entrySet()) {
      if (!campaignEntry.getValue().isGenerateFakePositions()) {
        continue;
      }

      try {
        for (Configuration configuration : campaignEntry.getKey().getConfigurations()) {
          Coordinates coordinates = getNextCoordinates(
              campaignEntry.getValue().getAgvPositions().get(configuration.getAgvId()));
          campaignEntry.getValue().getAgvPositions().put(configuration.getAgvId(), coordinates);

          PostAGVMeasurementRequest measurementRequest = getMeasurementRequest(
              campaignEntry.getKey(), configuration, campaignEntry.getValue());
          campaignEntry.getValue().getAgvMeasurementValueRequests()
              .put(configuration.getAgvId(), measurementRequest.getValues().get(0));

          PostAGVPositionRequest positionRequest = new PostAGVPositionRequest();

          positionRequest.setCampaignId(campaignEntry.getKey().getId());
          positionRequest.setCoordinates(List.of(coordinates));

          log.info(
              "Generating fake AGV position data for campaign {} and AGV {} at position ({}, {}) with measurement data ({}, {}).",
              campaignEntry.getKey().getId(), configuration.getAgvId(),
              positionRequest.getCoordinates().get(0).getX(),
              positionRequest.getCoordinates().get(0).getY(),
              measurementRequest.getValues().get(0).getDataRate(),
              measurementRequest.getValues().get(0).getLatency());

          agvPositionService.post(positionRequest, configuration.getAgvId());

          if (campaignEntry.getValue().isGenerateFakeMeasurements()) {
            agvMeasurementService.post(measurementRequest);
          }
        }
      } catch (Exception e) {
        log.warn("Error while posting AGV position data for campaign {}.",
            campaignEntry.getKey().getId(), e);
      }
    }
  }

  public void generateFakeMeasurements(PostAGVPositionRequest request, String agvId) {
    Map.Entry<Campaigns, FakeState> campaignEntry = campaigns.entrySet().stream()
        .filter(entry -> entry.getKey().getId().equals(request.getCampaignId())).findFirst()
        .orElse(null);

    if (campaignEntry == null || !campaignEntry.getValue().isGenerateFakeMeasurements()) {
      return;
    }

    Configuration configuration = campaignEntry.getKey().getConfigurations().stream()
        .filter(config -> config.getAgvId() != null && config.getAgvId().equals(agvId)).findAny()
        .orElse(null);

    if (configuration == null) {
      return;
    }

    FakeState fakeState = campaignEntry.getValue();

    for (Coordinates coordinates : request.getCoordinates()) {
      fakeState.getAgvPositions().put(configuration.getAgvId(), coordinates);

      PostAGVMeasurementRequest measurementRequest = getMeasurementRequest(campaignEntry.getKey(),
          configuration, campaignEntry.getValue());
      campaignEntry.getValue().getAgvMeasurementValueRequests()
          .put(configuration.getAgvId(), measurementRequest.getValues().get(0));

      log.info(
          "Generating fake AGV measurement data for campaign {} and AGV {} at position ({}, {}) with measurement data ({}, {}).",
          campaignEntry.getKey().getId(), configuration.getAgvId(), coordinates.getX(),
          coordinates.getY(), measurementRequest.getValues().get(0).getDataRate(),
          measurementRequest.getValues().get(0).getLatency());

      try {
        agvMeasurementService.post(measurementRequest);
      } catch (Exception e) {
        log.warn("Error while posting AGV measurement data for campaign {}.",
            campaignEntry.getKey().getId(), e);
      }
    }
  }

  private Coordinates getNextCoordinates(Coordinates previousCoordinates) {
    Coordinates coordinates = new Coordinates();

    coordinates.setMeasureMoment(LocalDateTime.now());

    if (previousCoordinates == null) {
      coordinates.setX(
          RandomUtils.insecure().randomFloat(0f, AGV_AREA_MAX.getX() - AGV_AREA_MIN.getX())
              + AGV_AREA_MIN.getX());
      coordinates.setY(
          RandomUtils.insecure().randomFloat(0f, AGV_AREA_MAX.getY() - AGV_AREA_MIN.getY())
              + AGV_AREA_MIN.getY());
      coordinates.setZ(0f);

      setRandomRotation(coordinates);
    } else {
      coordinates.setX(
          previousCoordinates.getX() + (AGV_SPEED * previousCoordinates.getRotationX()));
      coordinates.setY(
          previousCoordinates.getY() + (AGV_SPEED * previousCoordinates.getRotationY()));
      coordinates.setRotationX(previousCoordinates.getRotationX());
      coordinates.setRotationY(previousCoordinates.getRotationY());

      limitCoordinates(coordinates);
    }

    return coordinates;
  }

  private void setRandomRotation(Coordinates coordinates) {
    coordinates.setRotationX(
        RandomUtils.insecure().randomFloat(0f, 1f) * (RandomUtils.insecure().randomBoolean() ? 1f
            : -1f));
    coordinates.setRotationY(
        1f - coordinates.getRotationX() * (RandomUtils.insecure().randomBoolean() ? 1f : -1f));
  }

  private void setRotation(Coordinates coordinates, boolean setByX, boolean inverse) {
    int direction = inverse ? -1 : 1;

    if (setByX) {
      coordinates.setRotationX(RandomUtils.insecure().randomFloat(0.3f, 0.8f) * direction);
      coordinates.setRotationY(
          1f - coordinates.getRotationX() * (RandomUtils.insecure().randomBoolean() ? 1f : -1f));
    } else {
      coordinates.setRotationY(RandomUtils.insecure().randomFloat(0.3f, 0.8f) * direction);
      coordinates.setRotationX(
          1f - coordinates.getRotationY() * (RandomUtils.insecure().randomBoolean() ? 1f : -1f));
    }
  }

  private void limitCoordinates(Coordinates coordinates) {
    if (coordinates.getX() >= AGV_AREA_MAX.getX() || coordinates.getY() >= AGV_AREA_MAX.getY()
        || coordinates.getX() <= AGV_AREA_MIN.getX() || coordinates.getY() <= AGV_AREA_MIN.getY()) {
      if (coordinates.getX() >= AGV_AREA_MAX.getX()) {
        setRotation(coordinates, true, true);
      } else if (coordinates.getY() >= AGV_AREA_MAX.getY()) {
        setRotation(coordinates, false, true);
      } else if (coordinates.getX() <= AGV_AREA_MIN.getX()) {
        setRotation(coordinates, true, false);
      } else {
        setRotation(coordinates, false, false);
      }

      coordinates.setX(coordinates.getX() + (AGV_SPEED * coordinates.getRotationX()));
      coordinates.setY(coordinates.getY() + (AGV_SPEED * coordinates.getRotationY()));

      log.info("Setting new direction {}.", coordinates);
    }
  }

  private PostAGVMeasurementRequest getMeasurementRequest(Campaigns campaign,
      Configuration configuration, FakeState state) {
    PostAGVMeasurementRequest measurementRequest = new PostAGVMeasurementRequest();

    measurementRequest.setCampaignId(campaign.getId());

    PostAGVMeasurementValueRequest valueRequest = new PostAGVMeasurementValueRequest();
    valueRequest.setCampaignId(campaign.getId());
    valueRequest.setAgvId(configuration.getAgvId());
    valueRequest.setClientId(configuration.getClientId());
    valueRequest.setEndpointId(configuration.getEndpointId());

    valueRequest.setMeasureMoment(ZonedDateTime.now());
    valueRequest.setLocationX(state.getAgvPositions().get(configuration.getAgvId()).getX());
    valueRequest.setLocationY(state.getAgvPositions().get(configuration.getAgvId()).getY());
    valueRequest.setLocationZ(state.getAgvPositions().get(configuration.getAgvId()).getZ());

    if (state.getAgvMeasurementValueRequests().get(configuration.getAgvId()) == null) {
      valueRequest.setDataRate(RandomUtils.insecure().randomFloat(DATA_RATE_MIN, DATA_RATE_MAX));
      valueRequest.setLatency(RandomUtils.insecure().randomFloat(LATENCY_MIN, LATENCY_MAX));
    } else {
      if (RandomUtils.insecure().randomFloat(0f, 1f) < 0.07f) {
        state.setMeasurementDirection(state.getMeasurementDirection() * -1);
      }

      valueRequest.setDataRate(Math.min(DATA_RATE_MAX, Math.max(DATA_RATE_MIN,
          state.getAgvMeasurementValueRequests().get(configuration.getAgvId()).getDataRate() + (
              RandomUtils.insecure().randomFloat(0f, 37f) * state.getMeasurementDirection()))));
      valueRequest.setLatency(Math.min(LATENCY_MAX, Math.max(LATENCY_MIN,
          state.getAgvMeasurementValueRequests().get(configuration.getAgvId()).getLatency() + (
              RandomUtils.insecure().randomFloat(0f, 10f) * state.getMeasurementDirection()))));
    }

    valueRequest.setMeasurementId(state.getMeasurementId().getAndIncrement());

    measurementRequest.setValues(List.of(valueRequest));

    return measurementRequest;
  }

  @Data
  private static class FakeState {

    private final boolean generateFakePositions;

    private final boolean generateFakeMeasurements;

    private AtomicLong measurementId = new AtomicLong(1L);

    private Map<String, Coordinates> agvPositions = new HashMap<>();

    private Map<String, PostAGVMeasurementValueRequest> agvMeasurementValueRequests = new HashMap<>();

    private int measurementDirection = 1;
  }
}
