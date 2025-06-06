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

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.Campaigns;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandType;
import com.desire.utils.UserSession;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVCommandValidationService {

  private final @NonNull CampaignRepository campaignDao;

  public void validatePost(String agvId, PostAgvCommandRequest request) throws ValidationException {
    Validation validations = new Validation();

    validateCampaign(request.getCampaignId(), agvId, validations);

    validateData(request, validations);

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  public void validatePatch(String agvId, Optional<AGVCommands> commandOptional)
      throws ValidationException {
    Validation validations = new Validation();

    if (commandOptional.isEmpty()) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "AGV command not found");

      throw new ValidationException(validations);
    }

    AGVCommands command = commandOptional.get();

    if (!agvId.equals(command.getAgvId())) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Invalid command for given AGV");
    }

    validateCampaign(command.getCampaign().getId(), agvId, validations);

    if (!command.isPending()) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Not allowed to set state of completed command");
    }

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  private void validateCampaign(String campaignId, String agvId, Validation validations) {
    if (StringUtils.isNotEmpty(campaignId)) {
      Optional<Campaigns> campaignOptional = campaignDao.findById(campaignId);

      if (campaignOptional.isPresent()) {
        Campaigns campaign = campaignOptional.get();

        // User can only work within their own organization.
        if (!UserSession.organizationId().equals(campaign.getSite().getOrganization().getId())) {
          validations.addError(SystemErrorCodes.FORBIDDEN,
              "User is not allowed to manage campaign");
        }

        // Campaign must be active.
        if (!campaign.isActive()) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION, "Campaign must be active");
        }

        // Campaign must have given AGV configured.
        if (campaign.getConfigurations() == null || campaign.getConfigurations().stream()
            .noneMatch(configuration -> agvId.equals(configuration.getAgvId()))) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION,
              "AGV is not configured for given Campaign");
        }
      } else {
        validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Campaign not found");
      }
    }
  }

  private void validateData(PostAgvCommandRequest request, Validation validations) {
    if (AGVCommandType.GO_TO.equals(request.getEventType()) && request.getData() == null
        || request.getData().getCoordinates() == null) {
      validations.addError(SystemErrorCodes.INVALID_CONFIGURATION,
          "Coordinates must be present for GO_TO command");
    }
  }
}
