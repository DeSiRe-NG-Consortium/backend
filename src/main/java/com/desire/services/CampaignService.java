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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.dtos.PostCampaignRequest;
import com.desire.dtos.SearchCampaignRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.QCampaigns;
import com.desire.model.entities.Sites;
import com.desire.model.entities.TuiCampaignMessages;
import com.desire.model.repositories.CampaignRepository;
import com.desire.model.repositories.SiteRepository;
import com.desire.model.repositories.TuiCampaignMessageRepository;
import com.desire.tasks.TuiDataPushTask;
import com.desire.types.AGVCommandType;
import com.desire.types.CampaignState;
import com.desire.utils.UserSession;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CampaignService {

  private final @NonNull AGVCommandService agvCommandService;
  private final @NonNull FakePositionService fakePositionService;
  private final @NonNull CampaignRepository campaignDao;
  private final @NonNull SiteRepository siteDao;
  private final @NonNull TuiCampaignService tuiCampaignService;
  private final @NonNull TuiCampaignMessageRepository tuiCampaignDao;
  private final @NonNull TuiDataPushTask tuiSyncTask;

  public Page<Campaigns> search(SearchCampaignRequest request) {
    BooleanBuilder query = new BooleanBuilder();
    QCampaigns qCampaign = QCampaigns.campaigns;

    query.and(qCampaign.site.organization.id.eq(UserSession.organizationId()));

    if (StringUtils.isNotBlank(request.getId())) {
      query.and(qCampaign.id.eq(request.getId()));
    }

    if (StringUtils.isNotBlank(request.getName())) {
      query.and(qCampaign.name.eq(request.getName()));
    }

    if (StringUtils.isNotBlank(request.getSiteId())) {
      query.and(qCampaign.site.id.eq(request.getSiteId()));
    }

    if (request.getState() != null) {
      query.and(qCampaign.state.eq(request.getState()));
    }

    if (Boolean.TRUE.equals(request.getActive())) {
      query.and(qCampaign.state.in(
          Set.of(CampaignState.CREATED, CampaignState.RUNNING, CampaignState.COMPLETE_PENDING)));
    } else if (Boolean.FALSE.equals(request.getActive())) {
      query.and(qCampaign.state.in(Set.of(CampaignState.ABORTED, CampaignState.COMPLETED)));
    }

    if (request.getCreateDateFrom() != null) {
      query.and(qCampaign.createMoment.goe(request.getCreateDateFrom().atStartOfDay()));
    }

    if (request.getCreateDateTo() != null) {
      query.and(qCampaign.createMoment.loe(request.getCreateDateTo().atTime(LocalTime.MAX)));
    }

    return campaignDao.findAll(query, request.getPage(Campaigns.Fields.createMoment));
  }

  @Transactional
  public Campaigns post(PostCampaignRequest request) {
    Optional<Sites> siteOptional = siteDao.findById(request.getSiteId());

    if (siteOptional.isPresent()) {
      Campaigns campaign = save(request.toEntity(siteOptional.get()), HttpMethod.POST);

      if (campaign.isRunning()) {
        sendAgvCommands(campaign, AGVCommandType.START_CAMPAIGN);
      }

      fakePositionService.start(campaign,
          request.getGenerateFakePositions() != null && request.getGenerateFakePositions(),
          request.getGenerateFakeMeasurements() != null && request.getGenerateFakeMeasurements());

      return campaign;
    } else {
      log.info("Cannot create campaign due to invalid site ID '{}'.", request.getSiteId());
      return null;
    }
  }

  @Transactional
  public Campaigns patch(String id, PatchCampaignRequest request) {
    Optional<Campaigns> dbCampaign = campaignDao.findById(id);

    if (dbCampaign.isEmpty()) {
      return null;
    }

    Campaigns campaign = dbCampaign.get();

    if (StringUtils.isNotBlank(request.getName())) {
      campaign.setName(request.getName());
    }

    if (request.getConfigurations() != null) {
      campaign.setConfigurations(request.getConfigurations());
    }

    if (request.getState() != null) {
      campaign.setState(request.getState());

      if (campaign.isRunning()) {
        campaign.setStartMoment(LocalDateTime.now());

        sendAgvCommands(campaign, AGVCommandType.START_CAMPAIGN);
      }

      // If the campaign gets patched to COMPLETED and there are AGV in this campaign, use the
      // COMPLETE_PENDING state instead until the AGV reports to be finished with the last command.
      if (campaign.isCompleted()) {
        if (campaign.getConfigurations() != null && campaign.getConfigurations().stream()
            .anyMatch(configuration -> configuration.getAgvId() != null)) {
          campaign.setState(CampaignState.COMPLETE_PENDING);

          sendAgvCommands(campaign, AGVCommandType.COMPLETE_CAMPAIGN);
        } else {
          campaign.setStopMoment(LocalDateTime.now());
          fakePositionService.stop(campaign);
        }
      }

      if (campaign.isAborted()) {
        campaign.setStopMoment(LocalDateTime.now());
        fakePositionService.stop(campaign);

        sendAgvCommands(campaign, AGVCommandType.ABORT_CAMPAIGN);
      }
    }

    return save(campaign, HttpMethod.PATCH);
  }

  @Transactional
  public Campaigns completeCampaign(Campaigns campaign) {
    // TODO: Only complete the campaign once all AGVs are finished if multiple ones are configured.

    campaign.setState(CampaignState.COMPLETED);
    campaign.setStopMoment(LocalDateTime.now());

    campaign = save(campaign, HttpMethod.PATCH);

    return campaign;
  }

  /**
   * Save the campaign and create a message in the TUI backend queue.
   * 
   * @param campaign The campaign to save.
   * @param sendMethod The HTTP method to use for sending the message.
   * @return The saved campaign.
   */
  private Campaigns save(Campaigns campaign, HttpMethod sendMethod) {
    campaign.setUpdateMoment(LocalDateTime.now());

    campaign = campaignDao.save(campaign);

    // Save the campaign message in queue for TUI backend.
    tuiCampaignDao.save(new TuiCampaignMessages(campaign, sendMethod));

    // Execute the synchronization task
    tuiSyncTask.executeAsync();

    return campaign;
  }

  private void sendAgvCommands(Campaigns campaign, AGVCommandType eventType) {
    for (Campaigns.Configuration configuration : campaign.getConfigurations()) {
      if (configuration.getAgvId() != null) {
        if (!campaign.isActive()) {
          agvCommandService.cancelPendingCommands(configuration.getAgvId(), campaign);
        }

        PostAgvCommandRequest request = PostAgvCommandRequest.builder().campaignId(campaign.getId())
            .eventType(eventType).measureMoment(LocalDateTime.now()).build();

        agvCommandService
            .saveAndScheduleCommand(request.toEntity(configuration.getAgvId(), campaign), true);
      }
    }
  }
}
