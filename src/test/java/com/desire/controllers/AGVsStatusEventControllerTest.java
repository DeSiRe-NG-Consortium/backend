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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.Pagination;
import com.desire.utils.TestDataService;

class AGVsStatusEventControllerTest extends BaseMvcTest {

  @Autowired
  private TestDataService testDataService;

  private Sites site;

  private FakeUser agvUser;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");

    site = testDataService.getOrCreateSite("Test site", organization);

    agvUser = FakeUser.builder().organizationId(organization.getId()).roles(List.of(Roles.AGV))
        .keycloakId(UUID.randomUUID().toString()).build();

    mockKeycloakUser(agvUser);
  }

  @Test
  void testGet() throws Exception {
    Campaigns campaign = testDataService.getOrCreateCampaign("status-event-" + UUID.randomUUID(),
        CampaignState.RUNNING, site);

    AGVStatusEvents statusEvent1 = testDataService.createAGVStatusEvent(agvUser.getKeycloakId(),
        AGVStatusEventType.ONLINE, null);
    AGVCommands command =
        testDataService.createAGVCommand(agvUser.getKeycloakId(), AGVCommandType.GO_TO, campaign);
    AGVStatusEvents statusEvent2 = testDataService.createAGVStatusEvent(agvUser.getKeycloakId(),
        AGVStatusEventType.ACKNOWLEDGE_REQUEST, command);
    AGVStatusEvents statusEvent3 = testDataService.createAGVStatusEvent(agvUser.getKeycloakId(),
        AGVStatusEventType.COMPLETE_REQUEST, command);
    AGVStatusEvents statusEvent4 = testDataService.createAGVStatusEvent(agvUser.getKeycloakId(),
        AGVStatusEventType.SHUTDOWN, null);

    // Status event from other AGV should not show up.
    testDataService.createAGVStatusEvent(UUID.randomUUID().toString(), AGVStatusEventType.ONLINE,
        null);

    MvcResult result = mockMvc
        .perform(
            MockMvcRequestBuilders.get(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId())
                .with(agvUser.get()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    // Ensure GET is returning the previously created entity.
    List<AGVStatusEvents> statusEvents = parseSearchList(result, AGVStatusEvents.class);
    assertEquals(4, statusEvents.size());
    assertEquals(statusEvent1.getId(), statusEvents.get(3).getId());
    assertEquals(statusEvent2.getId(), statusEvents.get(2).getId());
    assertEquals(statusEvent3.getId(), statusEvents.get(1).getId());
    assertEquals(statusEvent4.getId(), statusEvents.get(0).getId());
  }
}
