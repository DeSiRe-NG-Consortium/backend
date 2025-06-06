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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostCampaignRequest;
import com.desire.dtos.SearchCampaignRequest.Fields;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.validations.codes.SystemErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;

class CampaignsControllerTest extends BaseMvcTest {

  private Organizations organization;

  private FakeUser user;

  @BeforeEach
  void setup() {
    organization = testDataService.getOrCreateOrganization("Test Org.");

    user = FakeUser.builder().organizationId(organization.getId()).roles(List.of(Roles.OPERATOR))
        .build();
  }

  @Test
  void testGet() throws Exception {
    Sites site1 = testDataService.getOrCreateSite("GET campaigns test site", organization);
    Sites site2 = testDataService.getOrCreateSite("Other test site", organization);

    testDataService.getOrCreateCampaign("created", CampaignState.CREATED, site1);
    testDataService.getOrCreateCampaign("running", CampaignState.RUNNING, site1);
    testDataService.getOrCreateCampaign("complete_pending", CampaignState.COMPLETE_PENDING, site1);
    testDataService.getOrCreateCampaign("completed", CampaignState.COMPLETED, site1);
    testDataService.getOrCreateCampaign("aborted", CampaignState.ABORTED, site1);

    testDataService.getOrCreateCampaign("running", CampaignState.RUNNING, site2);

    // Get all for given site.
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.Campaigns.PATH).with(user.get())
            .param(Fields.siteId, site1.getId()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<Campaigns> campaigns = parseSearchList(result, Campaigns.class);

    assertEquals(5, campaigns.size());

    // Get active for given site.
    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.Campaigns.PATH).with(user.get())
            .param(Fields.siteId, site1.getId()).param(Fields.active, Boolean.TRUE.toString()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    campaigns = parseSearchList(result, Campaigns.class);

    assertEquals(3, campaigns.size());
    assertTrue(campaigns.stream()
        .allMatch(campaign -> CampaignState.CREATED.equals(campaign.getState())
            || CampaignState.RUNNING.equals(campaign.getState())
            || CampaignState.COMPLETE_PENDING.equals(campaign.getState())));

    // Get inactive for give site.
    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.Campaigns.PATH).with(user.get())
            .param(Fields.siteId, site1.getId()).param(Fields.active, Boolean.FALSE.toString()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    campaigns = parseSearchList(result, Campaigns.class);

    assertEquals(2, campaigns.size());
    assertTrue(
        campaigns.stream().allMatch(campaign -> CampaignState.COMPLETED.equals(campaign.getState())
            || CampaignState.ABORTED.equals(campaign.getState())));

    // Get given state for given site.
    result = mockMvc.perform(MockMvcRequestBuilders.get(Endpoints.Campaigns.PATH).with(user.get())
        .param(Fields.siteId, site1.getId()).param(Fields.state, CampaignState.CREATED.toString()))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    campaigns = parseSearchList(result, Campaigns.class);

    assertEquals(1, campaigns.size());
    assertEquals(CampaignState.CREATED, campaigns.get(0).getState());
  }

  /**
   * Success: Create and complete campaign.
   */
  @Test
  void testCampaignStateFlow() throws Exception {
    // Create campaign.
    Configuration configuration = new Configuration();
    configuration.setClientId("test-client");
    configuration.setEndpointId("test-endpoint");
    configuration.setOrchestratorId("test-orchestrator");

    Campaigns campaign = createCampaign("TEST", CampaignState.CREATED, List.of(configuration),
        testDataService.getOrCreateSite("Test site", organization));
    assertNotNull(campaign);
    assertNotNull(campaign.getId());
    assertNull(campaign.getStartMoment());
    assertNull(campaign.getStopMoment());

    String newName = "TEST_2";
    campaign = updateCampaign(campaign.getId(), newName, CampaignState.RUNNING);
    assertEquals(newName, campaign.getName());
    assertTrue(campaign.isRunning());
    assertNotNull(campaign.getStartMoment());
    assertNull(campaign.getStopMoment());

    campaign = updateCampaign(campaign.getId(), null, CampaignState.COMPLETED);
    assertTrue(campaign.isCompleted());
    assertNotNull(campaign.getStartMoment());
    assertNotNull(campaign.getStopMoment());
  }

  /**
   * Fail: Test POST validations.
   */
  @Test
  void testPostCampaignValidations() throws Exception {
    // Test annotation-based validations.
    Organizations otherOrganization = testDataService.getOrCreateOrganization("Other Org");
    Sites otherSite = testDataService.getOrCreateSite("Other Site", otherOrganization);

    String agvId = UUID.randomUUID().toString();

    mockKeycloakUser(agvId, otherOrganization, null, List.of(Roles.AGV));

    Configuration configuration = new Configuration();
    configuration.setClientId("test-client");
    configuration.setEndpointId("test-endpoint");
    configuration.setOrchestratorId("test-orchestrator");
    // 1. AGV belonging to other organization.
    configuration.setAgvId(agvId);

    PostCampaignRequest postRequest = new PostCampaignRequest();
    postRequest.setName("invalid-campaign");
    // 2. Invalid campaign state.
    postRequest.setState(CampaignState.COMPLETED);
    postRequest.setSiteId(otherSite.getId());

    postRequest.setConfigurations(List.of(configuration));

    mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.VALIDATION_ERROR.toString())));

    // Test service-based validations.
    mockKeycloakUser(agvId, organization, otherSite, List.of(Roles.AGV));

    postRequest.setState(CampaignState.CREATED);
    // 1. Site belonging to other organization.
    postRequest.setSiteId(otherSite.getId());

    mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.FORBIDDEN.toString())));

    // 2. AGV registered with other site.
    Sites site = testDataService.getOrCreateSite("Test site", organization);

    postRequest.setSiteId(site.getId());

    mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_CONFIGURATION.toString())));
  }

  @Test
  void testPostCampaignAvgValidations() throws Exception {
    Sites site = testDataService.getOrCreateSite("Test site", organization);
    Sites otherSite = testDataService.getOrCreateSite("Other Site", organization);

    String agvId = UUID.randomUUID().toString();

    mockKeycloakUser(agvId, organization, site, List.of(Roles.AGV));

    Configuration configuration = new Configuration();
    Random random = new Random();
    configuration.setEndpointId("endpoint-" + random.nextInt());
    configuration.setClientId("client-" + random.nextInt());
    configuration.setOrchestratorId("orchestrator-" + random.nextInt());
    configuration.setAgvId(agvId);

    PostCampaignRequest postRequest = new PostCampaignRequest();
    postRequest.setName("invalid-avg-campaign");
    postRequest.setState(CampaignState.RUNNING);
    postRequest.setSiteId(otherSite.getId());
    postRequest.setConfigurations(List.of(configuration));

    // 1. AGV registered with other site and AGV offline.
    mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_CONFIGURATION.toString())))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.RESOURCE_NOT_AVAILABLE.toString())));

    postRequest.setSiteId(site.getId());

    testDataService.setAgvOnline(agvId);

    // 2. There can only be one running campaign for an AGV.
    // Create a RUNNING campaign.
    mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk());

    // Create another campaign for the AGV with state CREATED.
    postRequest.setState(CampaignState.CREATED);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    Campaigns campaign = parse(result, Campaigns.class);

    // Patching the second campaign to RUNNING should fail.
    PatchCampaignRequest patchCampaignRequest = new PatchCampaignRequest();
    patchCampaignRequest.setState(CampaignState.RUNNING);

    String response = mockMvc
        .perform(MockMvcRequestBuilders
            .patch(Endpoints.Campaigns.PATH + Endpoints.Campaigns.ID, campaign.getId())
            .with(user.get()).content(om.writeValueAsString(patchCampaignRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse()
        .getContentAsString();

    JsonNode errorsNode = om.readTree(response).path("errors");

    boolean agvMessageExists = errorsNode.toString()
        .contains("AGV ID " + agvId + " already assigned to a running campaign");

    assertTrue(agvMessageExists, "AGV ID error message is missing");
  }

  /**
   * Fail: Test PATCH validations.
   */
  @Test
  void testPatchCampaignValidations() throws Exception {
    // Create campaign.
    Configuration configuration = new Configuration();
    configuration.setClientId("test-client");
    configuration.setEndpointId("test-endpoint");
    configuration.setOrchestratorId("test-orchestrator");

    Campaigns campaign = createCampaign("TEST", CampaignState.RUNNING, List.of(configuration),
        testDataService.getOrCreateSite("Test site", organization));

    // Test annotation-based validations.
    Organizations otherOrganization = testDataService.getOrCreateOrganization("Other Org");

    String agvId = UUID.randomUUID().toString();

    mockKeycloakUser(agvId, otherOrganization, null, List.of(Roles.AGV));

    PatchCampaignRequest patchRequest = new PatchCampaignRequest();
    // 2. Invalid campaign state.
    patchRequest.setState(CampaignState.COMPLETED);
    patchRequest.setConfigurations(List.of(configuration));

    FakeUser otherUser = FakeUser.builder().organizationId(otherOrganization.getId())
        .roles(List.of(Roles.OPERATOR)).build();

    mockMvc
        .perform(MockMvcRequestBuilders
            .patch(Endpoints.Campaigns.PATH + Endpoints.Campaigns.ID, campaign.getId())
            .with(otherUser.get()).content(om.writeValueAsString(patchRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.FORBIDDEN.toString())))
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors[*].code")
            .value(Matchers.hasItem(SystemErrorCodes.INVALID_OPERATION.toString())));
  }

  /**
   * Success: Abort a campaign that is in state COMPLETE_PENDING.
   */
  @Test
  void testAbortCompletePendingCampaign() throws Exception {
    Campaigns campaign = testDataService.getOrCreateCampaign("complete_test",
        CampaignState.COMPLETE_PENDING, testDataService.getOrCreateSite("Test site", organization));

    updateCampaign(campaign.getId(), null, CampaignState.ABORTED);
  }

  private Campaigns createCampaign(String name, CampaignState state,
      List<Configuration> configurations, Sites site) throws Exception {
    PostCampaignRequest postRequest = new PostCampaignRequest();
    postRequest.setName(name);
    postRequest.setState(state);
    postRequest.setSiteId(site.getId());

    postRequest.setConfigurations(configurations);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, Campaigns.class);
  }

  private Campaigns updateCampaign(String id, String name, CampaignState state) throws Exception {
    PatchCampaignRequest patchRequest = new PatchCampaignRequest();

    if (StringUtils.isNotEmpty(name)) {
      patchRequest.setName(name);
    }
    if (state != null) {
      patchRequest.setState(state);
    }

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.patch(Endpoints.Campaigns.PATH + Endpoints.Campaigns.ID, id)
            .with(user.get()).content(om.writeValueAsString(patchRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, Campaigns.class);
  }

}
