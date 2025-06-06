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

package com.desire.utils;

import com.desire.model.entities.Campaigns.Configuration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Coordinates;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.AGVPositionRepository;
import com.desire.model.repositories.AGVStatusEventRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.model.repositories.OrganizationRepository;
import com.desire.model.repositories.SiteRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;

@Component
public class TestDataService {

  @Autowired
  private AGVCommandRepository agvCommandDao;

  @Autowired
  private AGVPositionRepository agvPositionDao;

  @Autowired
  private AGVStatusEventRepository agvStatusEventDao;

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private OrganizationRepository organisationDao;

  @Autowired
  private SiteRepository siteDao;

  private final Random random = new Random();

  public Campaigns createCampaign(String name, CampaignState state, Sites site,
      Configuration configuration) {
    Campaigns campaign = new Campaigns();
    campaign.setName(name);
    campaign.setState(state);
    campaign.setSite(site);
    campaign.setConfigurations(List.of(configuration));

    return campaignDao.save(campaign);
  }

  public Campaigns getOrCreateCampaign(String name, CampaignState state, Sites site) {
    Campaigns campaign = new Campaigns();
    campaign.setName(name);
    campaign.setState(state);
    campaign.setSite(site);

    Optional<Campaigns> campaignOptional = campaignDao
        .findAll(
            Example.of(campaign, ExampleMatcher.matching().withIgnorePaths(Campaigns.Fields.id)))
        .stream().min(Comparator.comparing(Campaigns::getId));

    return campaignOptional.orElseGet(() -> campaignDao.save(campaign));
  }

  public Organizations getOrCreateOrganization(String name) {
    Organizations organization = new Organizations();
    organization.setName(name);

    Optional<Organizations> organizationOptional = organisationDao
        .findAll(Example.of(organization,
            ExampleMatcher.matching().withIgnorePaths(Organizations.Fields.id)))
        .stream().min(Comparator.comparing(Organizations::getId));

    return organizationOptional.orElseGet(() -> organisationDao.save(organization));
  }

  public Sites getOrCreateSite(String name, Organizations organization) {
    Sites site = new Sites();
    site.setName(name);
    site.setOrganization(organization);

    Optional<Sites> siteOptional = siteDao
        .findAll(Example.of(site, ExampleMatcher.matching().withIgnorePaths(Sites.Fields.id)))
        .stream().min(Comparator.comparing(Sites::getId));

    return siteOptional.orElseGet(() -> siteDao.save(site));
  }

  public void setAgvOnline(String agvId) {
    createAGVStatusEvent(agvId, AGVStatusEventType.ONLINE, null);
  }

  public AGVStatusEvents createAGVStatusEvent(String agvId, AGVStatusEventType type,
      AGVCommands command) {
    AGVStatusEvents statusEvent = new AGVStatusEvents();
    statusEvent.setAgvId(agvId);
    statusEvent.setMeasureMoment(LocalDateTime.now());
    statusEvent.setEventType(type);
    statusEvent.setCommand(command);

    return agvStatusEventDao.save(statusEvent);
  }

  public AGVCommands createAGVCommand(String agvId, AGVCommandType type, Campaigns campaign) {
    AGVCommands command = new AGVCommands();
    command.setType(type);
    command.setCampaign(campaign);
    command.setMeasureMoment(LocalDateTime.now());
    command.setAgvId(agvId);
    command.setState(AGVCommandState.CREATED);

    return agvCommandDao.save(command);
  }

  public AGVPositions createAGVPosition(String agvId, Campaigns campaign) {
    AGVPositions agvPosition = new AGVPositions();

    agvPosition.setAgvId(agvId);
    agvPosition.setCampaign(campaign);
    agvPosition.setCoordinates(List.of(getRandomCoordinates(null), getRandomCoordinates(null),
        getRandomCoordinates(null)));

    return agvPositionDao.save(agvPosition);
  }

  public Coordinates getRandomCoordinates(LocalDateTime measureMoment) {
    return new Coordinates(measureMoment != null ? measureMoment : LocalDateTime.now(),
        random.nextFloat(100f), random.nextFloat(100f), random.nextFloat(100f), 0f, 0f,
        random.nextFloat(360f));
  }
}
