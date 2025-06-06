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

import com.desire.constants.Endpoints.TUI;
import com.desire.dtos.GetInterpolatedMeasurementResponse;
import com.desire.dtos.GetInterpolatedMeasurementsRequest;
import com.desire.dtos.GetInterpolatedMeasurementsRequest.GetInterpolatedMeasurementsLocationRequest;
import com.desire.dtos.GetInterpolatedMeasurementValueResponse;
import com.desire.dtos.SearchCampaignRequest;
import com.desire.dtos.SearchInterpolatedMeasurementsRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Coordinates;
import com.desire.model.entities.Sites;
import com.desire.utils.TuiTimestampDeserializer;
import com.desire.validations.exceptions.ValidationException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class InterpolatedMeasurementsService {

  private final @NonNull CampaignService campaignService;
  private final @NonNull InterpolatedMeasurementsValidationService validationService;
  private final @NonNull RestTemplate restTemplateShortTimeout;

  private final ObjectMapper tuiObjectMapper;
  private final CollectionType tuiResponseListType;

  @Value("${TUI_BACKEND_URL}" + TUI.INTERPOLATED_MEASUREMENTS_BULK)
  private String tuiBackendUrl;


  @Autowired
  public InterpolatedMeasurementsService(@NonNull CampaignService campaignService,
      @NonNull InterpolatedMeasurementsValidationService validationService,
      @NonNull RestTemplate restTemplateShortTimeout) {
    this.campaignService = campaignService;
    this.validationService = validationService;
    this.restTemplateShortTimeout = restTemplateShortTimeout;

    JavaTimeModule timeModule = new JavaTimeModule();
    timeModule.addDeserializer(ZonedDateTime.class, new TuiTimestampDeserializer());

    this.tuiObjectMapper = JsonMapper.builder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
        .addModule(timeModule).build();

    this.tuiResponseListType = tuiObjectMapper.getTypeFactory()
        .constructCollectionType(ArrayList.class,
            GetInterpolatedMeasurementValueResponse.class);
  }

  public GetInterpolatedMeasurementResponse queryInterpolatedMeasurements(String campaignId,
      SearchInterpolatedMeasurementsRequest searchRequest) throws ValidationException {
    SearchCampaignRequest searchCampaignRequest = SearchCampaignRequest.builder().id(campaignId)
        .build();
    Optional<Campaigns> campaignOptional = campaignService.search(searchCampaignRequest).stream()
        .findFirst();

    GetInterpolatedMeasurementResponse response = new GetInterpolatedMeasurementResponse();

    if (campaignOptional.isEmpty()) {
      return response;
    }

    validationService.validateSearch(campaignOptional.get(), searchRequest);

    Sites site = campaignOptional.get().getSite();

    GetInterpolatedMeasurementsRequest getRequest = new GetInterpolatedMeasurementsRequest(
        campaignId);

    Coordinates minCoordinates = site.getMinCoordinates();

    if (searchRequest.isMinCoordinatesPresent()) {
      minCoordinates = new Coordinates(searchRequest.getMinX(), searchRequest.getMinY(), 0f);
    }

    Coordinates maxCoordinates = site.getMaxCoordinates();

    if (searchRequest.isMaxCoordinatesPresent()) {
      maxCoordinates = new Coordinates(searchRequest.getMaxX(), searchRequest.getMaxY(), 0f);
    }

    if (maxCoordinates.getX() < minCoordinates.getX()) {
      maxCoordinates.setX(minCoordinates.getX());
    }

    if (maxCoordinates.getY() < minCoordinates.getY()) {
      maxCoordinates.setY(minCoordinates.getY());
    }

    LocalDateTime startTime = LocalDateTime.now();

    List<GetInterpolatedMeasurementsLocationRequest> locations = new ArrayList<>();

    float x = minCoordinates.getX();
    float y = minCoordinates.getY();
    float resolution;

    if (searchRequest.getTileResolution() != null) {
      resolution = searchRequest.getTileResolution();
    } else if (site.getMapTileResolution() != null) {
      resolution = site.getMapTileResolution();
    } else {
      resolution = 1f;
    }

    if (resolution <= 0) {
      resolution = site.getMapTileResolution();
    }

    while (x <= maxCoordinates.getX()) {
      while (y <= maxCoordinates.getY()) {
        locations.add(new GetInterpolatedMeasurementsLocationRequest(x, y));

        y += resolution;

      }

      y = minCoordinates.getY();
      x += resolution;
    }

    getRequest.setLocations(locations);

    response.setTileResolution(resolution);
    response.setMinCoordinates(minCoordinates);
    response.setMaxCoordinates(maxCoordinates);

    if (Boolean.TRUE.equals(searchRequest.getGenerateFakeData())) {
      generateFakeData(campaignId, locations, response);
    } else {
      fetchData(campaignId, getRequest, response);
    }

    log.info("Got response with {} values in {}.", response.getValues().size(),
        Duration.between(startTime, LocalDateTime.now()));

    return response;
  }

  private void fetchData(String campaignId, GetInterpolatedMeasurementsRequest getRequest,
      GetInterpolatedMeasurementResponse result) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<String> rawResponse;

      rawResponse = restTemplateShortTimeout.exchange(tuiBackendUrl, HttpMethod.GET,
          new HttpEntity<>(getRequest, headers), String.class);

      if (rawResponse.getStatusCode().is2xxSuccessful()) {
        result.setValues(tuiObjectMapper.readValue(rawResponse.getBody(), tuiResponseListType));
        result.setDataReceived(true);
      } else {
        log.error("Failed to pull AGV measurements for campaign {} with response {}.", campaignId,
            rawResponse.getStatusCode());
      }
    } catch (Exception e) {
      log.error("Failed to pull AGV measurements for campaign {}: {}", campaignId, e.getMessage());
    }
  }

  private void generateFakeData(String campaignId,
      List<GetInterpolatedMeasurementsLocationRequest> locations,
      GetInterpolatedMeasurementResponse result) {
    List<GetInterpolatedMeasurementValueResponse> fakeValues = new ArrayList<>();

    for (GetInterpolatedMeasurementsLocationRequest location : locations) {
      GetInterpolatedMeasurementValueResponse fakeValue = new GetInterpolatedMeasurementValueResponse();

      fakeValue.setCampaignId(campaignId);
      fakeValue.setX(location.x());
      fakeValue.setY(location.y());

      if (RandomUtils.insecure().randomFloat(0f, 1f) < 0.95f) {
        fakeValue.setDatarate(RandomUtils.insecure().randomFloat(5f, 300f));
        fakeValue.setLatency(RandomUtils.insecure().randomFloat(1f, 100f));
      } else {
        // Insert some NaN randomly as this value can be returned by TUI as well.
        fakeValue.setDatarate(Float.NaN);
        fakeValue.setLatency(Float.NaN);
      }

      fakeValues.add(fakeValue);
    }

    result.setValues(fakeValues);
    result.setDataReceived(false);
  }
}
