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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.QCampaigns;
import com.desire.model.repositories.AGVStatusEventRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.Pagination;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVStatusService {

  private final @NonNull AGVStatusEventRepository agvStatusEventDao;
  private final @NonNull CampaignRepository campaignDao;

  public AGVStatusEvents getLatestStatusEvent(String agvId) {
    return agvStatusEventDao.findFirstByAgvIdOrderByCreateMomentDesc(agvId).orElse(null);
  }

  public Campaigns getCurrentCampaign(String agvId) {
    BooleanBuilder query = new BooleanBuilder();
    QCampaigns qCampaign = QCampaigns.campaigns;

    query.and(qCampaign.configurations.any().agvId.eq(agvId));

    query.and(qCampaign.state.eq(CampaignState.RUNNING));

    Page<Campaigns> page =
        campaignDao.findAll(query, Pagination.ONE_LATEST.getPage(Campaigns.Fields.createMoment));

    return page.get().findFirst().orElse(null);
  }

  public boolean isAgvOnline(String agvId) {
    Optional<AGVStatusEvents> latestAgvLifecycleEvent =
        agvStatusEventDao.findFirstByAgvIdAndEventTypeInOrderByCreateMomentDesc(agvId,
            AGVStatusEvents.AGV_LIFECYCLE_EVENT_TYPES);

    return latestAgvLifecycleEvent.isPresent()
        && AGVStatusEventType.ONLINE.equals(latestAgvLifecycleEvent.get().getEventType());
  }
}
