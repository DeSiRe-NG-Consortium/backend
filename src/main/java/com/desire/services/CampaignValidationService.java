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
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostCampaignRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.CampaignRepository;
import com.desire.model.repositories.SiteRepository;
import com.desire.types.CampaignState;
import com.desire.utils.Constants;
import com.desire.utils.KeycloakService;
import com.desire.utils.UserSession;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CampaignValidationService {

  private final @NonNull AGVStatusService agvStatusService;
  private final @NonNull CampaignRepository campaignDao;
  private final @NonNull KeycloakService keycloakService;
  private final @NonNull SiteRepository siteDao;

  public void validatePost(PostCampaignRequest request) throws ValidationException {
    Validation validations = new Validation();

    Optional<Sites> siteOptional = siteDao.findById(request.getSiteId());

    if (siteOptional.isEmpty()) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Site not found");
      throw new ValidationException(validations);
    }

    if (!siteOptional.get().getOrganization().getId().equals(UserSession.organizationId())) {
      validations.addError(SystemErrorCodes.FORBIDDEN, "User is not allowed to use site");
    }

    validateCampaignConfiguration(request.getConfigurations(), request.getState(), null,
        siteOptional.get(), validations);

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  public void validatePatch(String id, PatchCampaignRequest request) throws ValidationException {
    Validation validations = new Validation();
    Optional<Campaigns> dbCampaign = campaignDao.findById(id);

    // First check the campaign exists on DB.
    if (dbCampaign.isEmpty()) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "Campaign not found");
      throw new ValidationException(validations);
    }

    Campaigns campaign = dbCampaign.get();

    if (!campaign.getSite().getOrganization().getId().equals(UserSession.organizationId())) {
      validations.addError(SystemErrorCodes.FORBIDDEN,
          "User is not allowed to manage given campaign");
    }

    CampaignState stateToCheck =
        request.getState() != null ? request.getState() : campaign.getState();

    validateCampaignConfiguration(request.getConfigurations(), stateToCheck, campaign,
        campaign.getSite(), validations);

    if (request.getState() != null) {
      CampaignState state = request.getState();

      if (CampaignState.RUNNING.equals(state) && !campaign.isCreated()) {
        validations.addError(SystemErrorCodes.INVALID_OPERATION,
            "Only created campaigns can be started");
      }

      if (CampaignState.COMPLETED.equals(state) && !campaign.isActive()
          || CampaignState.ABORTED.equals(state) && campaign.isClosed()) {
        validations.addError(SystemErrorCodes.INVALID_OPERATION,
            "Only active campaigns can be closed");
      }
    }

    if (validations.hasErrors()) {
      throw new ValidationException(validations);
    }
  }

  private void validateCampaignConfiguration(List<Configuration> requestConfigurations,
      CampaignState state, Campaigns campaign, Sites site, Validation validations) {
    if (campaign != null && campaign.isRunning()
        && !CollectionUtils.isEmpty(requestConfigurations)) {
      validations.addError(SystemErrorCodes.INVALID_OPERATION,
          "Not allowed to change configuration of a running campaign");
    }

    List<Configuration> configurations = new ArrayList<>();

    // If configuration is given in request then use that (with POST or as PATCH, overriding the
    // existing configuration).
    if (requestConfigurations != null) {
      configurations.addAll(requestConfigurations);
    } else if (campaign != null && campaign.getConfigurations() != null) {
      // If no configuration is given in request, validate that existing one is still valid.
      configurations.addAll(campaign.getConfigurations());
    }

    for (Configuration configuration : configurations) {
      validateAgvConfig(configuration.getAgvId(), site, state, validations);
    }

    // When campaign is set to RUNNING state, must check the no configuration clientId, endpointId
    // or agvId are not already assigned in a RUNNING campaign
    if (CampaignState.RUNNING.equals(state) && !CollectionUtils.isEmpty(configurations)) {
      configurations.forEach(config -> {
        if (StringUtils.isNotBlank(config.getClientId()) && campaignDao
            .existsByStateAndConfigurationsClientId(CampaignState.RUNNING, config.getClientId())) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION,
              "Client ID " + config.getClientId() + " already assigned to a running campaign");
        }

        if (StringUtils.isNotBlank(config.getEndpointId())
            && campaignDao.existsByStateAndConfigurationsEndpointId(CampaignState.RUNNING,
                config.getEndpointId())) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION,
              "Endpoint ID " + config.getEndpointId() + " already assigned to a running campaign");
        }

        if (StringUtils.isNotBlank(config.getAgvId()) && campaignDao
            .existsByStateAndConfigurationsAgvId(CampaignState.RUNNING, config.getAgvId())) {
          validations.addError(SystemErrorCodes.INVALID_OPERATION,
              "AGV ID " + config.getAgvId() + " already assigned to a running campaign");
        }
      });
    }
  }

  private void validateAgvConfig(String agvId, Sites site, CampaignState campaignState,
      Validation validations) {
    if (StringUtils.isBlank(agvId) || CampaignState.ABORTED.equals(campaignState)) {
      return;
    }

    Optional<UserRepresentation> agvUser = keycloakService.getUserById(agvId);

    if (agvUser.isPresent()) {
      List<String> agvSites = agvUser.get().getAttributes().get(Constants.UserAttributes.SITE_ID);

      if (agvSites == null) {
        validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND,
            "No sites configured for given AGV");

        return;
      }

      if (!agvSites.contains(site.getId())) {
        validations.addError(SystemErrorCodes.INVALID_CONFIGURATION,
            "AGV is not registered with given site");
      }

      if (CampaignState.RUNNING.equals(campaignState)) {
        validateAgvIsOnline(agvId, validations);
      }
    } else {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_FOUND, "AGV not found");
    }
  }

  private void validateAgvIsOnline(String agvId, Validation validations) {
    if (!agvStatusService.isAgvOnline(agvId)) {
      validations.addError(SystemErrorCodes.RESOURCE_NOT_AVAILABLE, "AGV is not online");
    }
  }
}
