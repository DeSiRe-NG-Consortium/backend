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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.desire.constants.Endpoints.TUI;
import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.PostAGVMeasurementRequest;
import com.desire.dtos.PostAGVMeasurementRequest.PostAGVMeasurementValueRequest;
import com.desire.dtos.SearchAGVMeasurementRequest;
import com.desire.events.AGVStreamService;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.AGVMeasurements.Fields;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.QAGVMeasurements;
import com.desire.model.repositories.AGVMeasurementRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.utils.UserSession;
import com.desire.validations.exceptions.ValidationException;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVMeasurementService {

  private final @NonNull AGVMeasurementRepository measurementDao;
  private final @NonNull AGVMeasurementValidationService measurementValidationService;
  private final @NonNull AGVStreamService agvStreamService;
  private final @NonNull CampaignRepository campaignDao;
  private final @NonNull RestTemplate restTemplate;
  private final @NonNull RestTemplate restTemplateShortTimeout;

  @Value("${TUI_BACKEND_URL}" + TUI.CAMPAIGN_RESULTS)
  private String tuiBackendUrl;

  /**
   * Measurements with z value of -100 are not considered valid.
   */
  private static final float INVALID_THRESHOLD = -100f;

  public static final ParameterizedTypeReference<List<PostAGVMeasurementValueRequest>> REQUEST_TYPE =
      new ParameterizedTypeReference<>() {};


  public Page<AGVMeasurements> search(SearchAGVMeasurementRequest request) {
    BooleanBuilder query = new BooleanBuilder();
    QAGVMeasurements qMeasurement = QAGVMeasurements.aGVMeasurements;

    // filter by user organization
    query.and(qMeasurement.campaign.site.organization.id.eq(UserSession.organizationId()));

    if (StringUtils.isNotBlank(request.getCampaignId())) {
      query.and(qMeasurement.campaign.id.eq(request.getCampaignId()));
    }

    if (StringUtils.isNotBlank(request.getAgvId())) {
      query.and(qMeasurement.campaign.configurations.any().agvId.eq(request.getAgvId()));
    }

    if (request.getFrom() != null) {
      query.and(qMeasurement.coordinates.measureMoment.goe(request.getFrom()));
    }

    if (request.getTo() != null) {
      query.and(qMeasurement.coordinates.measureMoment.loe(request.getTo()));
    }

    query.and(qMeasurement.coordinates.z.gt(INVALID_THRESHOLD));

    return measurementDao.findAll(query, request.getPage(Fields.createMoment));
  }

  public List<AGVMeasurements> post(PostAGVMeasurementRequest request) throws ValidationException {
    measurementValidationService.validatePost(request);

    Optional<Campaigns> campaign = campaignDao.findById(request.getCampaignId());

    List<AGVMeasurements> measurements =
        campaign
            .map(campaigns -> measurementDao.saveAll(request.getValues().stream()
                .map(requestValue -> requestValue.toEntity(campaigns)).toList()))
            .orElseGet(List::of);

    publishToAgvStreams(measurements);

    return measurements;
  }

  public void pullData(Campaigns campaign, boolean offlineSync) {

    Optional<AGVMeasurements> latestMeasurement =
        measurementDao.findFirstByCampaignIdOrderByMeasurementIdDesc(campaign.getId());

    String requestUrl = tuiBackendUrl + "?campaignId=" + campaign.getId();

    if (latestMeasurement.isPresent()) {
      long startId = latestMeasurement.get().getMeasurementId() + 1;

      requestUrl += "&fromId=" + startId;

      log.info("Pulling AGV measurements for campaign {} from ID {} in {} mode.", campaign.getId(),
          startId, offlineSync ? "offline" : "online");
    } else {
      log.info("Pulling AGV measurements for campaign {} from the start in {} mode.",
          campaign.getId(), offlineSync ? "offline" : "online");
    }

    try {
      ResponseEntity<List<PostAGVMeasurementValueRequest>> response;

      if (offlineSync) {
        response =
            restTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity.EMPTY, REQUEST_TYPE);
      } else {
        response = restTemplateShortTimeout.exchange(requestUrl, HttpMethod.GET, HttpEntity.EMPTY,
            REQUEST_TYPE);
      }

      if (response.getStatusCode().is2xxSuccessful()) {
        success(campaign, response.getBody());
      } else {
        Exception exception = new Exception("HTTP error status: " + response.getStatusCode());
        failed(campaign, response, exception);
      }
    } catch (Exception e) {
      failed(campaign, null, e);
    }
  }

  private void success(Campaigns campaign, List<PostAGVMeasurementValueRequest> message) {
    PostAGVMeasurementRequest request = new PostAGVMeasurementRequest();

    request.setCampaignId(campaign.getId());
    request.setValues(message);

    try {
      post(request);
    } catch (ValidationException validationException) {
      log.error("Validation error in AGV measurements for campaign {}: {}", campaign.getId(),
          validationException.getValidation().getErrors());
    } catch (Exception e) {
      failed(campaign, null, e);
    }
  }

  private void failed(Campaigns campaign,
      ResponseEntity<List<PostAGVMeasurementValueRequest>> response, Exception exception) {
    if (response != null) {
      log.error("Failed to pull AGV measurements for campaign {} with response: {}",
          campaign.getId(), response, exception);
    } else {
      log.error("Failed to pull AGV measurements for campaign {}.", campaign.getId(), exception);
    }
  }

  private void publishToAgvStreams(List<AGVMeasurements> measurements) {
    Map<String, List<AGVMeasurements>> groupedMeasurements = new HashMap<>();

    for (AGVMeasurements measurement : measurements) {
      if (measurement.getCoordinates() != null
          && measurement.getCoordinates().getZ() <= INVALID_THRESHOLD) {
        continue;
      }

      Set<String> agvIds = measurement.getCampaign().getConfigurations().stream()
          .map(Configuration::getAgvId).collect(Collectors.toSet());

      for (String agvId : agvIds) {
        groupedMeasurements.putIfAbsent(agvId, new ArrayList<>());

        List<AGVMeasurements> agvMeasurements = groupedMeasurements.get(agvId);

        agvMeasurements.add(measurement);

        groupedMeasurements.put(agvId, agvMeasurements);
      }
    }

    groupedMeasurements
        .forEach((key, value) -> agvStreamService.publish(key, new AGVUpdateDto(value)));

  }
}
