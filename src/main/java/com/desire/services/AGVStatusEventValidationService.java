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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.Campaigns;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.UserSession;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVStatusEventValidationService {

  private final @NonNull AGVCommandRepository agvCommandDao;
  private final @NonNull CampaignRepository campaignDao;

  public void validatePost(PostAgvStatusEventRequest request) throws ValidationException {
    Validation validations = new Validation();

    AGVCommands command;

    if (request.getCommandId() != null) {
      Optional<AGVCommands> commandOptional = agvCommandDao.findById(request.getCommandId());
      if (commandOptional.isEmpty()) {
        validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Command reference not found");

        return;
      }
      command = commandOptional.get();
    } else {
      command = null;
    }

    validateCommandReference(request, command, validations);

    validateCampaign(command, validations);

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  private void validateCampaign(AGVCommands command, Validation validations) {
    if (command != null) {
      Optional<Campaigns> campaign = campaignDao.findById(command.getCampaign().getId());

      if (campaign.isEmpty()) {
        validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Campaign reference not found");

        return;
      }

      // User can only work within their own organization.
      if (!UserSession.organizationId()
          .equals(campaign.get().getSite().getOrganization().getId())) {
        validations.addError(SystemErrorCodes.FORBIDDEN);
      }

      // Valid campaign/command states:
      // 1. Campaign is running.
      // 2. Campaign is pending to close and command is COMPLETE_CAMPAIGN.
      // 3. Campaign is aborted and command is ABORT_CAMPAIGN.
      if (!(campaign.get().isRunning()
          || (CampaignState.COMPLETE_PENDING.equals(campaign.get().getState())
              && AGVCommandType.COMPLETE_CAMPAIGN.equals(command.getType()))
          || (CampaignState.ABORTED.equals(command.getCampaign().getState())
              && AGVCommandType.ABORT_CAMPAIGN.equals(command.getType())))) {
        validations.addError(SystemErrorCodes.INVALID_OPERATION, "Campaign must be active");
      }
    }
  }

  private void validateCommandReference(PostAgvStatusEventRequest request, AGVCommands command,
      Validation validations) {
    if (!request.relatesToCommand()) {
      return;
    }

    if (command == null) {
      validations.addError(SystemErrorCodes.VALIDATION_ERROR,
          "Command reference is required for given status event type");

      return;
    }

    if (command.isClosed()) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Referenced command is already closed");
    }

    if (AGVStatusEventType.ACKNOWLEDGE_REQUEST.equals(request.getEventType())
        && !AGVCommandState.SENT.equals(command.getState())) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Only sent commands can be acknowledged");
    }

    if (AGVStatusEventType.ACKNOWLEDGE_REQUEST.equals(request.getEventType())
        && AGVCommandState.ACKNOWLEDGED.equals(command.getState())) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Command has already been acknowledged");
    }

    if (AGVStatusEventType.COMPLETE_REQUEST.equals(request.getEventType())
        && !AGVCommandState.ACKNOWLEDGED.equals(command.getState())) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Command must be acknowledged first");
    }
  }
}
