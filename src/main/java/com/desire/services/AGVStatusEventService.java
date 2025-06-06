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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.events.AGVStreamService;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.QAGVStatusEvents;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.AGVStatusEventRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.Pagination;
import com.desire.validations.AGVIdValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVStatusEventService {

  private final @NonNull AGVCommandRepository agvCommandDao;
  private final @NonNull AGVCommandService agvCommandService;
  private final @NonNull AGVIdValidator agvIdValidator;
  private final @NonNull AGVStatusEventRepository agvStatusEventDao;
  private final @NonNull AGVStreamService agvStreamService;
  private final @NonNull CampaignRepository campaignDao;
  private final @NonNull CampaignService campaignService;

  public Page<AGVStatusEvents> search(String agvId, Pagination pagination) {
    if (!agvIdValidator.isValid(agvId, null)) {
      return Page.empty();
    }

    BooleanBuilder query = new BooleanBuilder();

    QAGVStatusEvents qEvents = QAGVStatusEvents.aGVStatusEvents;

    query.and(qEvents.agvId.eq(agvId));

    return agvStatusEventDao.findAll(query,
        pagination.getPage(AGVStatusEvents.Fields.measureMoment));
  }

  public AGVStatusEvents post(String agvId, PostAgvStatusEventRequest request) {
    AGVCommands command;
    if (request.getCommandId() != null) {
      command = agvCommandDao.findById(request.getCommandId()).orElse(null);
    } else {
      command = null;
    }

    AGVStatusEvents statusEvent = agvStatusEventDao.save(request.toEntity(agvId, command));

    if (AGVStatusEventType.ONLINE.equals(statusEvent.getEventType())) {
      log.info("AGV {} is now online.", agvId);

      // When AGV reports to be online, schedule the next command.
      agvCommandService.scheduleNextCampaignCommand(agvId);
    } else if (AGVStatusEventType.SHUTDOWN.equals(statusEvent.getEventType())) {
      log.info("AGV {} is shutting down.", agvId);
    } else if (command != null) {
      // If event references a command, update the command state accordingly.
      log.info("AGV {} sent {} status for {} command.", agvId, statusEvent.getEventType(),
          command.getType());

      command.setStateFromStatusEvent(statusEvent);

      // Complete the campaign once the AGV has reported that it is finished with the last command.
      if (command.getCampaign() != null
          && CampaignState.COMPLETE_PENDING.equals(command.getCampaign().getState())
          && AGVStatusEventType.COMPLETE_REQUEST.equals(request.getEventType())) {
        campaignService.completeCampaign(command.getCampaign());
      }

      agvCommandService.saveAndScheduleCommand(command);
    }

    statusEvent = agvStatusEventDao.save(statusEvent);

    agvStreamService.publish(agvId, new AGVUpdateDto(statusEvent));

    return statusEvent;
  }
}
