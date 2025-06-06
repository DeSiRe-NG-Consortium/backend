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

package com.desire.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints.AGVs;
import com.desire.constants.Roles;
import com.desire.dtos.AGVUpdateDto;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.TestDataService;

class AGVUpdatesTest extends BaseMvcTest {

  @Autowired
  private AGVCommandRepository agvCommandDao;

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private TestDataService testDataService;

  private FakeUser agvUser;

  private FakeUser operatorUser;

  private Campaigns campaign;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");

    Sites site = testDataService.getOrCreateSite("Test site", organization);

    agvUser = FakeUser.builder().organizationId(organization.getId()).siteId(site.getId())
        .keycloakId(UUID.randomUUID().toString()).roles(List.of(Roles.AGV)).build();

    operatorUser = FakeUser.builder().organizationId(organization.getId())
        .roles(List.of(Roles.OPERATOR)).build();

    campaign = testDataService.getOrCreateCampaign("TEST-" + UUID.randomUUID(),
        CampaignState.RUNNING, site);

    Configuration configuration = new Configuration();
    configuration.setAgvId(agvUser.getKeycloakId());
    campaign.setConfigurations(List.of(configuration));
    campaign = campaignDao.save(campaign);

    mockKeycloakUser(agvUser);
  }

  @Test
  void testAgvUpdates() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Prepare test data.
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    AGVCommands command = testDataService.createAGVCommand(agvUser.getKeycloakId(),
        AGVCommandType.START_CAMPAIGN, campaign);
    command.setState(AGVCommandState.SENT);
    command = agvCommandDao.save(command);

    // Subscribe to the stream.
    ResultActions resultActions =
        mockMvc
            .perform(MockMvcRequestBuilders.get(AGVs.PATH + AGVs.STREAM, agvUser.getKeycloakId())
                .with(operatorUser.get()))
            .andExpect(MockMvcResultMatchers.request().asyncStarted());

    // Generate data to be published on the stream.
    postAgvStatusEvent(AGVStatusEventType.ONLINE, null, agvUser);
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, command, agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, command, agvUser);

    AGVPositions position = postAgvPosition(campaign, List.of(LocalDateTime.now().minusSeconds(2L),
        LocalDateTime.now().minusSeconds(3L), LocalDateTime.now().minusSeconds(1L)), agvUser);
    postAgvPosition(campaign, agvUser);

    postAgvStatusEvent(AGVStatusEventType.SHUTDOWN, null, agvUser);

    // Get results and filter out heartbeat messages.
    List<AGVUpdateDto> updates =
        parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), AGVUpdateDto.class);

    updates = updates.stream()
        .filter(update -> update.getPosition() != null || update.getStatus() != null).toList();

    // Validate results.
    assertEquals(6, updates.size());

    assertEquals(AGVStatusEventType.ONLINE, updates.get(0).getStatus().getEventType());
    assertNull(updates.get(0).getPosition());

    assertEquals(AGVStatusEventType.ACKNOWLEDGE_REQUEST, updates.get(1).getStatus().getEventType());
    assertNull(updates.get(1).getPosition());

    assertEquals(AGVStatusEventType.COMPLETE_REQUEST, updates.get(2).getStatus().getEventType());
    assertNull(updates.get(2).getPosition());

    assertNotNull(updates.get(3).getPosition());
    // Validate that coordinates are sorted DESC.
    assertEquals(position.getCoordinates().get(0).getMeasureMoment(),
        updates.get(3).getPosition().getCoordinates().get(0).getMeasureMoment());
    assertNull(updates.get(3).getStatus());

    assertNotNull(updates.get(4).getPosition());
    assertNull(updates.get(4).getStatus());

    assertEquals(AGVStatusEventType.SHUTDOWN, updates.get(5).getStatus().getEventType());
    assertNull(updates.get(5).getPosition());
  }
}
