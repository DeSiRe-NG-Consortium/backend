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

import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.PostAGVPositionRequest;
import com.desire.dtos.SearchAGVPositionRequest;
import com.desire.events.AGVStreamService;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.QAGVPositions;
import com.desire.model.entities.TuiAGVPositionMessages;
import com.desire.model.repositories.AGVPositionRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.model.repositories.TuiAGVPositionMessageRepository;
import com.desire.tasks.TuiDataPushTask;
import com.desire.utils.UserSession;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import com.querydsl.core.BooleanBuilder;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVPositionService {

  private final @NonNull AGVPositionRepository positionDao;
  private final @NonNull AGVStreamService agvStreamService;
  private final @NonNull CampaignRepository campaignDao;
  private final @NonNull TuiAGVPositionMessageRepository tuiAgvPositionDao;
  private final @NonNull TuiDataPushTask tuiSyncTask;

  public Page<AGVPositions> search(SearchAGVPositionRequest request) {
    BooleanBuilder query = new BooleanBuilder();
    QAGVPositions qAgvPosition = QAGVPositions.aGVPositions;

    query.and(qAgvPosition.campaign.site.organization.id.eq(UserSession.organizationId()));

    if (StringUtils.isNotBlank(request.getAgvId())) {
      query.and(qAgvPosition.agvId.eq(request.getAgvId()));
    }

    if (StringUtils.isNotBlank(request.getCampaignId())) {
      query.and(qAgvPosition.campaign.site.organization.id.eq(UserSession.organizationId()));
      query.and(qAgvPosition.campaign.id.eq(request.getCampaignId()));
    } else {
      query.and(qAgvPosition.campaign.site.organization.id.eq(UserSession.organizationId())
          .or(qAgvPosition.campaign.isNull()));
    }

    if (request.getStartMoment() != null) {
      query.and(qAgvPosition.createMoment.goe(request.getStartMoment()));
    }

    if (request.getStopMoment() != null) {
      query.and(qAgvPosition.createMoment.loe(request.getStopMoment()));
    }

    return positionDao.findAll(query, request.getPage(AGVPositions.Fields.createMoment));
  }

  @Transactional
  public AGVPositions post(PostAGVPositionRequest request) {
    return processPost(request, UserSession.id());
  }

  @Transactional
  public AGVPositions post(PostAGVPositionRequest request, String userId) {
    return processPost(request, userId);
  }

  private AGVPositions processPost(PostAGVPositionRequest request, String userId) {
    Optional<Campaigns> campaign = campaignDao.findById(request.getCampaignId());

    if (campaign.isPresent()) {
      AGVPositions agvPosition = new AGVPositions();

      agvPosition.setAgvId(userId);
      agvPosition.setCampaign(campaign.get());
      agvPosition.getCoordinates().addAll(request.getCoordinates());

      // Save the position in DB.
      agvPosition = positionDao.save(agvPosition);

      // Save the position message in queue for TUI backend.
      tuiAgvPositionDao.save(new TuiAGVPositionMessages(agvPosition));

      // Execute the synchronization task
      tuiSyncTask.executeAsync();

      // Publish on AGV update stream.
      agvStreamService.publish(userId, new AGVUpdateDto(agvPosition));

      return agvPosition;
    }

    return null;
  }
}
