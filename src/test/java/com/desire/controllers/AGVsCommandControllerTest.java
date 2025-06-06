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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGVs.Commands;
import com.desire.constants.Roles;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.dtos.SearchAGVCommandRequest.Fields;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVCommands.AVGCommandData;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.TestDataService;
import com.desire.validations.codes.SystemErrorCodes;

class AGVsCommandControllerTest extends BaseMvcTest {

  @Autowired
  private AGVCommandRepository agvCommandDao;

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private TestDataService testDataService;

  private FakeUser operatorUser;
  private Campaigns campaign;
  private FakeUser agvUser;

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
  void testGet() throws Exception {
    String randomAgvId = UUID.randomUUID().toString();

    mockKeycloakUser(randomAgvId, campaign.getSite().getOrganization(), campaign.getSite(),
        List.of(Roles.AGV));

    AGVCommands command1 =
        testDataService.createAGVCommand(randomAgvId, AGVCommandType.GO_TO, campaign);
    AGVCommands command2 =
        testDataService.createAGVCommand(randomAgvId, AGVCommandType.GO_TO, campaign);

    command2.setState(AGVCommandState.ACKNOWLEDGED);
    agvCommandDao.save(command2);

    Campaigns campaign2 = testDataService.getOrCreateCampaign("Other campaign",
        CampaignState.RUNNING, campaign.getSite());

    AGVCommands command3 =
        testDataService.createAGVCommand(randomAgvId, AGVCommandType.GO_TO, campaign2);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Commands.PATH, randomAgvId).with(operatorUser.get()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVCommands> commands = parseList(result, AGVCommands.class);

    assertEquals(3, commands.size());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Commands.PATH, randomAgvId).with(operatorUser.get())
            .param(Fields.campaignId, campaign2.getId()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    commands = parseList(result, AGVCommands.class);

    assertEquals(1, commands.size());
    assertEquals(command3.getId(), commands.get(0).getId());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Commands.PATH, randomAgvId).with(operatorUser.get())
            .param(Fields.campaignId, campaign.getId())
            .param(Fields.pending, Boolean.TRUE.toString()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    commands = parseList(result, AGVCommands.class);

    assertEquals(1, commands.size());
    assertEquals(command1.getId(), commands.get(0).getId());
  }

  @Test
  void testPostValidationGoToWithoutCoordinates() throws Exception {
    PostAgvCommandRequest request = new PostAgvCommandRequest();
    request.setEventType(AGVCommandType.GO_TO);
    request.setData(new AVGCommandData());
    request.setMeasureMoment(LocalDateTime.now());
    request.setCampaignId(campaign.getId());

    mockMvc
        .perform(MockMvcRequestBuilders
            .post(Commands.PATH, campaign.getConfigurations().get(0).getAgvId())
            .with(operatorUser.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_CONFIGURATION.toString())));
  }

  /**
   * Sends AGV commands, subscribes to the stream and checks latest command is resent into stream
   */
  @Test
  void testStreamSendsLatestCommand() throws Exception {
    String agvUserId = agvUser.getKeycloakId();

    mockKeycloakUser(agvUserId, campaign.getSite().getOrganization(), campaign.getSite(),
        List.of(Roles.AGV));

    // insert two commands to check only latest one gets sent into stream when subscribing
    testDataService.createAGVCommand(agvUserId, AGVCommandType.GO_TO, campaign);
    AGVCommands command2 =
        testDataService.createAGVCommand(agvUserId, AGVCommandType.GO_TO, campaign);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGV.PATH + Endpoints.AGV.COMMANDS_STREAM)
            .with(agvUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted())
        .andExpect(MockMvcResultMatchers.status().isOk()).andDo(MockMvcResultHandlers.print())
        .andReturn();

    AGVCommands resumeStreamCommand = parseResumeStreamAgvCommand(result);

    assertEquals(command2.getId(), resumeStreamCommand.getId());
  }

  /**
   * Parses stream response that contains the heartbeat as a first message and the latest AGV
   * command available in a new line
   * 
   * @param result
   * @return
   * @throws Exception
   */
  private AGVCommands parseResumeStreamAgvCommand(MvcResult result) throws Exception {
    String jsonStreamResponse = result.getResponse().getContentAsString();
    String[] jsonParts = jsonStreamResponse.split("\n");

    return om.readValue(jsonParts[1], AGVCommands.class);
  }

}
