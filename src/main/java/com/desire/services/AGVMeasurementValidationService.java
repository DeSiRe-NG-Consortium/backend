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
import com.desire.dtos.PostMobileMeasurementRequest;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.repositories.AGVMeasurementRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.utils.UserSession;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVMeasurementValidationService {

  private final @NonNull AGVMeasurementRepository measurementDao;
  private final @NonNull CampaignRepository campaignDao;

  public void validatePost(PostAGVMeasurementRequest request) throws ValidationException {
    Validation validations = new Validation();

    Optional<Campaigns> campaignOptional = campaignDao.findById(request.getCampaignId());

    if (campaignOptional.isEmpty()) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Campaign not found");

      throw new ValidationException(validations);
    }

    Campaigns campaign = campaignOptional.get();

    if (request.getValues().stream()
        .anyMatch(value -> !campaign.getId().equals(value.getCampaignId()))) {
      validations.addError(SystemErrorCodes.VALIDATION_ERROR, "Campaign ID mismatch");
    }

    Set<String> agvIds = campaign.getConfigurations().stream().map(Configuration::getAgvId)
        .collect(Collectors.toSet());
    Set<String> clientIds = campaign.getConfigurations().stream().map(Configuration::getClientId)
        .collect(Collectors.toSet());
    Set<String> endpointIds = campaign.getConfigurations().stream()
        .map(Configuration::getEndpointId).collect(Collectors.toSet());

    for (PostAGVMeasurementValueRequest value : request.getValues()) {
      if (!agvIds.contains(value.getAgvId())) {
        validations.addError(SystemErrorCodes.VALIDATION_ERROR, "AGV ID mismatch");
      }

      if (!clientIds.contains(value.getClientId())) {
        validations.addError(SystemErrorCodes.VALIDATION_ERROR, "Client ID mismatch");
      }

      if (!endpointIds.contains(value.getEndpointId())) {
        validations.addError(SystemErrorCodes.VALIDATION_ERROR, "Endpoint ID mismatch");
      }
    }

    Optional<Long> maxMeasurementId = request.getValues().stream()
        .map(PostAGVMeasurementValueRequest::getMeasurementId).max(Long::compareTo);
    Optional<AGVMeasurements> latestMeasurmentOptional = measurementDao.findFirstByCampaignIdOrderByMeasurementIdDesc(
        campaign.getId());

    if (maxMeasurementId.isPresent() && latestMeasurmentOptional.isPresent()
        && latestMeasurmentOptional.get().getMeasurementId() >= maxMeasurementId.get()) {
      validations.addError(SystemErrorCodes.VALIDATION_ERROR, "Duplicate measurements");
    }

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }
}
