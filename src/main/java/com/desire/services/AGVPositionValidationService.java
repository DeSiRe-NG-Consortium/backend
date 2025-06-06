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
import com.desire.dtos.PostAGVPositionRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.repositories.CampaignRepository;
import com.desire.utils.UserSession;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVPositionValidationService {

  private final @NonNull CampaignRepository campaignDao;

  public void validatePost(PostAGVPositionRequest request) throws ValidationException {
    Validation validations = new Validation();

    Optional<Campaigns> campaign = campaignDao.findById(request.getCampaignId());

    if (campaign.isEmpty()) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Campaign/AGV not found");
      throw new ValidationException(validations);
    }

    // User can only work within their own organization.
    if (!UserSession.organizationId().equals(campaign.get().getSite().getOrganization().getId())) {
      validations.addError(SystemErrorCodes.FORBIDDEN,
          "User is not allowed to manage the given campaign.");
    }

    // Campaign must be RUNNING.
    if (campaign.get().isCreated() || campaign.get().isClosed()) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION, "Campaign must be RUNNING");
    }

    // AGV must be configured in campaign.
    if (campaign.get().getConfigurations().stream()
        .noneMatch(configuration -> configuration.getAgvId() != null)) {
      validations.addError(SystemErrorCodes.FORBIDDEN, "No AGV configured for given campaign.");
    } else if (campaign.get().getConfigurations().stream()
        .filter(configuration -> configuration.getAgvId() != null)
        .noneMatch(configuration -> configuration.getAgvId().equals(UserSession.id()))) {
      validations.addError(SystemErrorCodes.FORBIDDEN, "Given AGV is not part of given campaign");
    }

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

}
