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

package com.desire.controllers;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.TuiMessages;
import com.desire.constants.Roles;
import com.desire.model.entities.QTuiAGVPositionMessages;
import com.desire.model.entities.QTuiCampaignMessages;
import com.desire.model.entities.TuiAGVPositionMessages;
import com.desire.model.entities.TuiCampaignMessages;
import com.desire.model.repositories.TuiAGVPositionMessageRepository;
import com.desire.model.repositories.TuiCampaignMessageRepository;
import com.querydsl.core.BooleanBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Fake TUI controller to test the integration since we have no network connection to their backend
 */
@Tag(name = TuiMessages.TAG)
@Validated
@RestController
@RequestMapping(Endpoints.TuiMessages.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TuiMessagesController {

  private final @NonNull TuiCampaignMessageRepository tuiCampaignDao;
  private final @NonNull TuiAGVPositionMessageRepository tuiPositionDao;

  @RolesAllowed(Roles.TECHNICAL_ADMIN)
  @GetMapping(Endpoints.TuiMessages.CAMPAIGN)
  public Iterable<TuiCampaignMessages> getCampaignMessages(
      @RequestParam(required = false) String campaignId) {
    BooleanBuilder query = new BooleanBuilder();
    QTuiCampaignMessages qCampaign = QTuiCampaignMessages.tuiCampaignMessages;

    if (StringUtils.isNotBlank(campaignId)) {
      query.and(qCampaign.campaign.id.eq(campaignId));
    }

    return tuiCampaignDao.findAll(query,
        Sort.by(TuiCampaignMessages.Fields.createMoment).descending());
  }

  @RolesAllowed(Roles.TECHNICAL_ADMIN)
  @GetMapping(Endpoints.TuiMessages.AGV_POSITIONS)
  public Iterable<TuiAGVPositionMessages> getAgvPositionMessages(
      @RequestParam(required = false) String campaignId,
      @RequestParam(required = false) String agvPositionId) {
    BooleanBuilder query = new BooleanBuilder();
    QTuiAGVPositionMessages qPositions = QTuiAGVPositionMessages.tuiAGVPositionMessages;

    if (StringUtils.isNotBlank(campaignId)) {
      query.and(qPositions.position.campaign.id.eq(campaignId));
    }

    if (StringUtils.isNotBlank(agvPositionId)) {
      query.and(qPositions.position.id.eq(agvPositionId));
    }

    return tuiPositionDao.findAll(query,
        Sort.by(TuiAGVPositionMessages.Fields.createMoment).descending());
  }

  @Operation(summary = "TUI campaign queue synchronization query")
  @RolesAllowed(Roles.TECHNICAL_ADMIN)
  @GetMapping(Endpoints.TuiMessages.TUI_SYNC_CAMPAIGN)
  public List<TuiCampaignMessages> getCampaignMessages() {
    return tuiCampaignDao.findAllByOrderByCreateMomentAsc();
  }

  @Operation(summary = "TUI AGV positions queue synchronization query")
  @RolesAllowed(Roles.TECHNICAL_ADMIN)
  @GetMapping(Endpoints.TuiMessages.TUI_SYNC_AGV_POSITIONS)
  public List<TuiAGVPositionMessages> getAgvPositionMessages() {
    return tuiPositionDao.findAllByOrderByCreateMomentAsc();
  }

}
