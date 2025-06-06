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
import com.desire.dtos.PostMobileMeasurementRequest;
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
public class MobileMeasurementValidationService {

  private final @NonNull CampaignRepository campaignDao;

  public void validatePost(PostMobileMeasurementRequest request) throws ValidationException {
    Validation validations = new Validation();

    validateCampaign(request.getCampaignId(), validations);

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  private void validateCampaign(String campaignId, Validation validations) {
    if (StringUtils.isNotEmpty(campaignId)) {
      Optional<Campaigns> campaign = campaignDao.findById(campaignId);

      if (campaign.isPresent()) {
        // user can only work within it's own organization
        if (!UserSession.organizationId()
            .equals(campaign.get().getSite().getOrganization().getId())) {
          validations.addError(SystemErrorCodes.FORBIDDEN);
        }

        // campaign must be RUNNING
        if (!campaign.get().isRunning()) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION, "Campaign must be RUNNING");
        }
      }
    }
  }

}
