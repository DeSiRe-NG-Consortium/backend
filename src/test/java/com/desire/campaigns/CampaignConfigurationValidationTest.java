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

package com.desire.campaigns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PostCampaignRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.fasterxml.jackson.databind.JsonNode;

class CampaignConfigurationValidationTest extends BaseMvcTest {

  @Autowired
  CampaignRepository campaignDao;

  private final String clientId = "test-client";
  private final String endpointId = "test-endpoint";
  private final String agvId = UUID.randomUUID().toString();

  private Organizations organization;
  private Sites site;
  private FakeUser user;

  @BeforeEach
  void setup() {
    organization = testDataService.getOrCreateOrganization("Test Org.");
    site = testDataService.getOrCreateSite("example", organization);
    mockKeycloakUser(agvId, organization, null, List.of(Roles.AGV));

    user = FakeUser.builder().organizationId(organization.getId()).roles(List.of(Roles.OPERATOR))
        .build();
  }

  /**
   * Test the validation of campaign configuration when creating a new campaign with the same
   * configuration IDs as an existing RUNNING campaign.
   * @throws Exception
   */
  @Test
  void testConfigurationValidation() throws Exception {
    // Create a campaign with a configuration
    createRunningCampaign();

    // request to create a new RUNNING campaign with same configuration IDs
    PostCampaignRequest postRequest = new PostCampaignRequest();
    postRequest.setName("new_campaign");
    postRequest.setState(CampaignState.RUNNING);
    postRequest.setSiteId(site.getId());
    postRequest.getConfigurations().add(getConfiguration());

    String response = mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse()
        .getContentAsString();

    JsonNode errorsNode = om.readTree(response).path("errors");

    // assert there are exactly 3 INVALID_OPERATION errors
    long invalidOperationCount = errorsNode.findValues("code").stream()
        .filter(code -> "INVALID_OPERATION".equals(code.asText())).count();
    assertEquals(3, invalidOperationCount, "There should be exactly 3 INVALID_OPERATION errors");

    // validate the presence of each specific message
    boolean clientMessageExists = errorsNode.toString()
        .contains("Client ID " + clientId + " already assigned to a running campaign");
    boolean endpointMessageExists = errorsNode.toString()
        .contains("Endpoint ID " + endpointId + " already assigned to a running campaign");
    boolean agvMessageExists = errorsNode.toString()
        .contains("AGV ID " + agvId + " already assigned to a running campaign");

    assertTrue(clientMessageExists, "Client ID error message is missing");
    assertTrue(endpointMessageExists, "Endpoint ID error message is missing");
    assertTrue(agvMessageExists, "AGV ID error message is missing");
  }

  private Campaigns createRunningCampaign() {
    Campaigns campaign = new Campaigns();
    campaign.setState(CampaignState.RUNNING);
    campaign.setSite(site);
    campaign.getConfigurations().add(getConfiguration());

    return campaignDao.save(campaign);
  }

  private Configuration getConfiguration() {
    Configuration configuration = new Campaigns.Configuration();
    configuration.setClientId(clientId);
    configuration.setEndpointId(endpointId);
    configuration.setAgvId(agvId);
    configuration.setOrchestratorId("test-orchestrator");

    return configuration;
  }

}
