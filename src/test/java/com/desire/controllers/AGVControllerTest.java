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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGV;
import com.desire.constants.Endpoints.AGVs.Commands;
import com.desire.constants.Endpoints.AGVs.StatusEvents;
import com.desire.constants.Roles;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVCommands.AVGCommandData;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Coordinates;
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
import com.desire.utils.Pagination;
import com.desire.utils.TestDataService;
import com.desire.validations.codes.SystemErrorCodes;

class AGVControllerTest extends BaseMvcTest {

  @Autowired
  private AGVCommandRepository agvCommandDao;

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private TestDataService testDataService;

  private FakeUser agvUser;

  private FakeUser operatorUser;

  private Campaigns campaign;

  private Sites site;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");

    site = testDataService.getOrCreateSite("Test site", organization);

    agvUser = FakeUser.builder().organizationId(organization.getId()).siteId(site.getId())
        .keycloakId(UUID.randomUUID().toString()).roles(List.of(Roles.AGV)).build();

    operatorUser = FakeUser.builder().organizationId(organization.getId())
        .roles(List.of(Roles.OPERATOR)).build();

    campaign = testDataService.getOrCreateCampaign("TEST-" + UUID.randomUUID(),
        CampaignState.RUNNING, site);

    Campaigns.Configuration configuration = new Configuration();
    configuration.setAgvId(agvUser.getKeycloakId());
    campaign.setConfigurations(List.of(configuration));
    campaign = campaignDao.save(campaign);

    mockKeycloakUser(agvUser);
  }


  /**
   * Tests AGV Status CRUD and stream
   *
   * @throws Exception
   */
  @Test
  void testPostAndGet() throws Exception {
    PostAgvStatusEventRequest request = new PostAgvStatusEventRequest();
    request.setEventType(AGVStatusEventType.ONLINE);
    request.setMeasureMoment(LocalDateTime.now());

    // Create an AGV status event.
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    // Save AGV status even creation response to compare later on with GET.
    AGVStatusEvents postEntity = parse(result, AGVStatusEvents.class);

    result = mockMvc
        .perform(
            MockMvcRequestBuilders.get(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId())
                .with(agvUser.get()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    // Ensure GET is returning the previously created entity.
    List<AGVStatusEvents> getEntities = parseSearchList(result, AGVStatusEvents.class);
    assertEquals(postEntity, getEntities.get(0));
  }

  @Test
  void testPostValidationsNoCommand() throws Exception {
    PostAgvStatusEventRequest request = new PostAgvStatusEventRequest();
    request.setEventType(AGVStatusEventType.COMPLETE_REQUEST);
    request.setMeasureMoment(LocalDateTime.now());

    // Event without command reference.
    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.VALIDATION_ERROR.toString())));
  }

  @Test
  void testPostValidationsWrongCommandStates() throws Exception {
    PostAgvStatusEventRequest request = new PostAgvStatusEventRequest();
    request.setEventType(AGVStatusEventType.COMPLETE_REQUEST);
    request.setMeasureMoment(LocalDateTime.now());

    Campaigns completedCampaign = testDataService
        .getOrCreateCampaign("status-campaign-" + UUID.randomUUID(), CampaignState.RUNNING, site);

    AGVCommands command = testDataService.createAGVCommand(agvUser.getKeycloakId(),
        AGVCommandType.GO_TO, completedCampaign);

    request.setCommandId(command.getId());
    request.setEventType(AGVStatusEventType.ACKNOWLEDGE_REQUEST);

    // Acknowledge command that has not been sent/scheduled yet.
    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));

    // Complete command that has not been acknowledged yet.
    command.setState(AGVCommandState.SENT);
    agvCommandDao.save(command);

    request.setEventType(AGVStatusEventType.COMPLETE_REQUEST);

    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));

    // Acknowledge command that has already been acknowledged.
    command.setState(AGVCommandState.ACKNOWLEDGED);
    agvCommandDao.save(command);

    request.setEventType(AGVStatusEventType.ACKNOWLEDGE_REQUEST);

    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));

    command.setState(AGVCommandState.COMPLETED);
    agvCommandDao.save(command);

    // Event for completed command.
    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));
  }

  @Test
  void testPostValidationsClosedCampaign() throws Exception {
    PostAgvStatusEventRequest request = new PostAgvStatusEventRequest();
    request.setEventType(AGVStatusEventType.COMPLETE_REQUEST);
    request.setMeasureMoment(LocalDateTime.now());

    Campaigns completedCampaign = testDataService
        .getOrCreateCampaign("status-campaign-" + UUID.randomUUID(), CampaignState.COMPLETED, site);

    AGVCommands command = testDataService.createAGVCommand(agvUser.getKeycloakId(),
        AGVCommandType.GO_TO, completedCampaign);

    request.setCommandId(command.getId());
    request.setEventType(AGVStatusEventType.COMPLETE_REQUEST);

    // Event for completed command and completed campaign.
    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.AGVs.StatusEvents.PATH, agvUser.getKeycloakId()).with(agvUser.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));
  }

  /**
   * Tests AGV control CRUD and stream
   *
   * @throws Exception
   */
  @Test
  void testGetStream() throws Exception {
    PostAgvStatusEventRequest statusEventRequest = new PostAgvStatusEventRequest();
    statusEventRequest.setEventType(AGVStatusEventType.ONLINE);
    statusEventRequest.setMeasureMoment(LocalDateTime.now());

    mockMvc
        .perform(MockMvcRequestBuilders.post(StatusEvents.PATH, agvUser.getKeycloakId())
            .with(agvUser.get()).content(om.writeValueAsString(statusEventRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk());

    AVGCommandData commandData = new AVGCommandData();
    commandData.setCoordinates(new Coordinates(0f, 0f, 0f));

    PostAgvCommandRequest request = new PostAgvCommandRequest();
    request.setEventType(AGVCommandType.GO_TO);
    request.setData(commandData);
    request.setMeasureMoment(LocalDateTime.now());
    request.setCampaignId(campaign.getId());

    // Create an AGV control event.
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Commands.PATH, agvUser.getKeycloakId())
            .with(operatorUser.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    // Save AGV control event creation response to compare later on with GET.
    AGVCommands commandFromPost = parse(result, AGVCommands.class);

    // Get the posted command from stream
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());
    String jsonStreamResponse = resultActions.andExpect(MockMvcResultMatchers.status().isOk())
        .andDo(MockMvcResultHandlers.print()).andReturn().getResponse().getContentAsString();

    // skip heartbeat message and get the latest command
    String lastCommandJson = jsonStreamResponse.split("\n")[1];
    AGVCommands commandFromStream = om.readValue(lastCommandJson, AGVCommands.class);

    // Test the stream contains correct data.
    assertEquals(commandFromPost, commandFromStream);
    assertEquals(AGVCommandState.SENT, commandFromPost.getState());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Commands.PATH, agvUser.getKeycloakId())
            .with(agvUser.get()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    // Ensure get is returning the previously created entity.
    List<AGVCommands> getEntities = parseSearchList(result, AGVCommands.class);
    assertEquals(commandFromPost, getEntities.get(0));
  }
}
