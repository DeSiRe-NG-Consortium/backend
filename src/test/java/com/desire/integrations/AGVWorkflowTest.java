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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.desire.events.EventStreamService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import com.desire.constants.Roles;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVCommands.AVGCommandData;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.TestDataService;

class AGVWorkflowTest extends BaseMvcTest {

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

    Campaigns.Configuration configuration = new Configuration();
    configuration.setAgvId(agvUser.getKeycloakId());
    campaign.setConfigurations(List.of(configuration));
    campaign = campaignDao.save(campaign);

    mockKeycloakUser(agvUser);
  }

  @Test
  void testCommandWorkflow() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Create a campaign with state CREATED.
    campaign.setState(CampaignState.CREATED);
    campaign = campaignDao.save(campaign);

    // Create GO_TO event for CREATED campaign, validate that it is saved but not sent.
    AGVCommands firstGoToCommand = postGoToCommand();

    validateAgvCommand(firstGoToCommand, AGVCommandType.GO_TO, AGVCommandState.CREATED);

    // Patch the campaign and validate that the event (START_CAMPAIGN) has been sent.
    patchCampaign(CampaignState.RUNNING);

    // Subscribe the AGV to the command stream.
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    List<AGVCommands> resultCommands = getAgvCommands();

    assertEquals(2, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.START_CAMPAIGN, AGVCommandState.SENT);
    validateAgvCommand(resultCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.CREATED);

    // After the first event has been ACKNOWLEDGED, validate that the previous event (GO_TO) has
    // been sent.
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, resultCommands.get(0), agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, resultCommands.get(0), agvUser);

    resultCommands = getAgvCommands();

    assertEquals(2, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.START_CAMPAIGN,
        AGVCommandState.COMPLETED);
    validateAgvCommand(resultCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.SENT);

    // Create another GO_TO event, validate that it has not been sent.
    AGVCommands secondGoToCommand = postGoToCommand();

    validateAgvCommand(secondGoToCommand, AGVCommandType.GO_TO, AGVCommandState.CREATED);

    // Send a status update from AGV with GO_TO_FINISHED, then validate that the second GO_TO event
    // has been sent.
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, firstGoToCommand, agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, firstGoToCommand, agvUser);

    resultCommands = getAgvCommands();

    assertEquals(3, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(resultCommands.get(1), AGVCommandType.START_CAMPAIGN,
        AGVCommandState.COMPLETED);
    validateAgvCommand(resultCommands.get(2), AGVCommandType.GO_TO, AGVCommandState.COMPLETED);

    // Send a third GO_TO event, then abort the second GO_TO event, validate that the second has
    // been ABORTED and published on stream and third has been SENT.
    AGVCommands thirdGoToCommand = postGoToCommand();

    validateAgvCommand(thirdGoToCommand, AGVCommandType.GO_TO, AGVCommandState.CREATED);

    patchAgvCommand(secondGoToCommand);

    resultCommands = getAgvCommands();

    assertEquals(4, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(resultCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.ABORTED);

    // Send a COMPLETE_CAMPAIGN event, validate that the event is SENT and the third GO_TO event is
    // aborted.
    patchCampaign(CampaignState.COMPLETED);

    resultCommands = getAgvCommands();

    assertEquals(5, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.COMPLETE_CAMPAIGN,
        AGVCommandState.SENT);
    validateAgvCommand(resultCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.ABORTED);

    // Send a COMPLETE_REQUEST from the AGV and validate that the COMPLETE_CAMPAIGN command is then
    // closed.
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, resultCommands.get(0), agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, resultCommands.get(0), agvUser);

    resultCommands = getAgvCommands();

    assertEquals(5, resultCommands.size());
    validateAgvCommand(resultCommands.get(0), AGVCommandType.COMPLETE_CAMPAIGN,
        AGVCommandState.COMPLETED);

    List<AGVCommands> streamCommands =
        parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), AGVCommands.class);

    assertEquals(8, streamCommands.size());
    validateAgvCommand(streamCommands.get(1), AGVCommandType.START_CAMPAIGN, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(2), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(3), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(4), AGVCommandType.GO_TO, AGVCommandState.ABORTED);
    validateAgvCommand(streamCommands.get(5), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(6), AGVCommandType.GO_TO, AGVCommandState.ABORTED);
    validateAgvCommand(streamCommands.get(7), AGVCommandType.COMPLETE_CAMPAIGN,
        AGVCommandState.SENT);

    Optional<Campaigns> campaignOptional = campaignDao.findById(campaign.getId());
    assertTrue(campaignOptional.isPresent());
    assertEquals(CampaignState.COMPLETED, campaignOptional.get().getState());
  }

  @Test
  void testResumeCommand() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Create a campaign with state CREATED.
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    // Create GO_TO event.
    AGVCommands firstGoToCommand = postGoToCommand();

    // Subscribe the AGV to the command stream.
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    AGVCommands secondGoToCommand = postGoToCommand();

    // Fast-forward to complete GO-TO commands.
    postAgvStatusEvent(AGVStatusEventType.REJECT_REQUEST, firstGoToCommand, agvUser);
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, secondGoToCommand, agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, secondGoToCommand, agvUser);

    List<AGVCommands> streamCommands =
        parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), AGVCommands.class);

    // Validate that a RESUME_CAMPAIGN command was published after the second GO_TO command.
    assertEquals(4, streamCommands.size());
    validateAgvCommand(streamCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(2), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(3), AGVCommandType.RESUME_CAMPAIGN, AGVCommandState.SENT);
  }

  @Test
  void testResumeCommandNotSentWhenCampaignIsAborted() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Create a campaign with state CREATED.
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    // Create GO_TO event.
    AGVCommands firstGoToCommand = postGoToCommand();

    // Subscribe the AGV to the command stream.
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    AGVCommands secondGoToCommand = postGoToCommand();

    // Fast-forward to complete GO-TO commands.
    postAgvStatusEvent(AGVStatusEventType.REJECT_REQUEST, firstGoToCommand, agvUser);
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, secondGoToCommand, agvUser);
    patchCampaign(CampaignState.ABORTED);

    List<AGVCommands> streamCommands =
        parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), AGVCommands.class);

    // Validate that a RESUME_CAMPAIGN command is not sent and we get ABORT_CAMPAIGN instead
    assertEquals(4, streamCommands.size());
    validateAgvCommand(streamCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(2), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(3), AGVCommandType.ABORT_CAMPAIGN, AGVCommandState.SENT);
  }

  @Test
  void testResumeCommandNotSentWhenCampaignIsCompleted() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Create a campaign with state RUNNING.
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    // Create GO_TO event.
    AGVCommands firstGoToCommand = postGoToCommand();

    // Subscribe the AGV to the command stream.
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    AGVCommands secondGoToCommand = postGoToCommand();

    // Fast-forward to complete GO-TO commands.
    postAgvStatusEvent(AGVStatusEventType.REJECT_REQUEST, firstGoToCommand, agvUser);
    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, secondGoToCommand, agvUser);
    patchCampaign(CampaignState.COMPLETED);

    List<AGVCommands> streamCommands =
        parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), AGVCommands.class);

    // Validate that a RESUME_CAMPAIGN command is not sent and we get ABORT_CAMPAIGN instead
    assertEquals(4, streamCommands.size());
    validateAgvCommand(streamCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(2), AGVCommandType.GO_TO, AGVCommandState.SENT);
    validateAgvCommand(streamCommands.get(3), AGVCommandType.COMPLETE_CAMPAIGN,
        AGVCommandState.SENT);
  }

  @Test
  void testAbortCommandCompletion() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    patchCampaign(CampaignState.ABORTED);

    List<AGVCommands> agvCommands = getAgvCommands();

    assertEquals(1, agvCommands.size());
    assertEquals(AGVCommandState.SENT, agvCommands.get(0).getState());

    postAgvStatusEvent(AGVStatusEventType.ACKNOWLEDGE_REQUEST, agvCommands.get(0), agvUser);
    postAgvStatusEvent(AGVStatusEventType.COMPLETE_REQUEST, agvCommands.get(0), agvUser);

    agvCommands = getAgvCommands();
    assertEquals(AGVCommandState.COMPLETED, agvCommands.get(0).getState());
  }

  /**
   * Heartbeat message should be only sent initially to stream when there is no previous messages to
   * show when subscribing
   * 
   * @throws Exception
   */
  @Test
  void testStreamHeartbeat() throws Exception {
    testDataService.setAgvOnline(agvUser.getKeycloakId());

    // Create a campaign with state CREATED.
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    // Subscribe the AGV to the command stream.
    ResultActions resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    // Return stream content
    List<EventStreamService.Heartbeat> timestamps = parseFromStream(
        resultActions.andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(MockMvcResultHandlers.print()).andReturn(), EventStreamService.Heartbeat.class);

    // Stream should send heartbeat since no commands were submitted yet
    assertEquals(1, timestamps.size());

    // Send a command to the stream
    postGoToCommand();

    // Subscribe again after an initial command was sent
    resultActions = mockMvc
        .perform(MockMvcRequestBuilders.get(AGV.PATH + AGV.COMMANDS_STREAM).with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());

    List<AGVCommands> streamCommands = parseFromStream(resultActions.andExpect(MockMvcResultMatchers.status().isOk())
        .andDo(MockMvcResultHandlers.print()).andReturn(), AGVCommands.class);

    // ensure the first message is not a heartbeat but the command we previously sent
    assertEquals(2, streamCommands.size());
    validateAgvCommand(streamCommands.get(1), AGVCommandType.GO_TO, AGVCommandState.SENT);
  }

  private AGVCommands postGoToCommand() throws Exception {
    AVGCommandData commandData = new AVGCommandData();
    commandData.setCoordinates(testDataService.getRandomCoordinates(LocalDateTime.now()));

    PostAgvCommandRequest postCommandRequest = new PostAgvCommandRequest();
    postCommandRequest.setCampaignId(campaign.getId());
    postCommandRequest.setEventType(AGVCommandType.GO_TO);
    postCommandRequest.setData(commandData);
    postCommandRequest.setMeasureMoment(LocalDateTime.now());

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Commands.PATH, agvUser.getKeycloakId())
            .with(operatorUser.get()).content(om.writeValueAsString(postCommandRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, AGVCommands.class);
  }

  private AGVCommands patchAgvCommand(AGVCommands command) throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders
            .patch(Commands.PATH + Commands.ID, agvUser.getKeycloakId(), command.getId())
            .with(operatorUser.get()).content(om.writeValueAsString(AGVCommandState.ABORTED))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, AGVCommands.class);
  }

  private List<AGVCommands> getAgvCommands() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Commands.PATH, agvUser.getKeycloakId())
            .with(operatorUser.get()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parseList(result, AGVCommands.class);
  }

  private Campaigns patchCampaign(CampaignState state) throws Exception {
    PatchCampaignRequest patchCampaignRequest = new PatchCampaignRequest();
    patchCampaignRequest.setState(state);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders
            .patch(Endpoints.Campaigns.PATH + Endpoints.Campaigns.ID, campaign.getId())
            .with(operatorUser.get()).content(om.writeValueAsString(patchCampaignRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, Campaigns.class);
  }

  private void validateAgvCommand(AGVCommands command, AGVCommandType expectedType,
      AGVCommandState expectedState) {
    assertNotNull(command);
    assertEquals(expectedType, command.getType());
    assertEquals(expectedState, command.getState());
  }
}
