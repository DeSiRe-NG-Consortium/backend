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

package com.desire.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostCampaignRequest;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.entities.TuiCampaignMessages;
import com.desire.model.repositories.TuiAGVPositionMessageRepository;
import com.desire.model.repositories.TuiCampaignMessageRepository;
import com.desire.tasks.TuiDataPushTask;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;

class TuiPushTest extends BaseMvcTest {

  @MockitoSpyBean
  private TuiCampaignMessageRepository tuiCampaignDao;

  @MockitoSpyBean
  private TuiAGVPositionMessageRepository tuiPositionDao;

  @Autowired
  private TuiDataPushTask tuiSyncTask;

  private FakeUser user;

  private Organizations organization;

  private Sites site;

  @MockitoBean
  private RestTemplate restTemplate;

  private AutoCloseable mockitoAnnotations;

  @BeforeEach
  void setup() {
    organization = testDataService.getOrCreateOrganization("Test Org.");

    site = testDataService.getOrCreateSite("My site", organization);

    user = FakeUser.builder().organizationId(organization.getId()).siteId(site.getId())
        .keycloakId(UUID.randomUUID().toString()).roles(List.of(Roles.OPERATOR, Roles.AGV)).build();

    testDataService.setAgvOnline(user.getKeycloakId());

    mockKeycloakUser(user.getKeycloakId(), organization, site, user.getRoles());

    mockitoAnnotations = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void releaseMocks() throws Exception {
    mockitoAnnotations.close();
  }

  @Test
  void testCampaignTuiIntegration() throws Exception {
    Campaigns campaign = createCampaign();

    // verify a message was created in the tui queue
    assertEquals(1, tuiCampaignDao.findByCampaignId(campaign.getId()).size());

    // complete the campaign doing PATCH
    completeCampaign(campaign.getId());

    List<TuiCampaignMessages> campaignMessages = tuiCampaignDao.findByCampaignId(campaign.getId());

    // now there should be 2 messages in queue (POST + PATCH)
    assertEquals(2, campaignMessages.size());

    // check they got the right httpMethod
    assertEquals(HttpMethod.POST.toString(), campaignMessages.get(0).getHttpMethod());
    assertEquals(HttpMethod.PATCH.toString(), campaignMessages.get(1).getHttpMethod());

    // emulate success tui backend response
    when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
        eq(Void.class), any(Object[].class))).thenReturn(ResponseEntity.ok().build());

    // execute tui sync task to send pending messages
    tuiSyncTask.tuiDataSynchronization();

    // check all messages were successfully sent to TUI backend
    assertTrue(tuiCampaignDao.findByCampaignId(campaign.getId()).isEmpty());
  }

  @Test
  void testAgvPositionTuiIntegration() throws Exception {
    AGVPositions position = postAgvPosition(createCampaign(), user);

    // verify a message was created in the tui queue
    assertEquals(1, tuiPositionDao.findByPositionId(position.getId()).size());

    when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
        eq(Void.class), any(Object[].class))).thenReturn(ResponseEntity.ok().build());

    // execute tui sync task to send pending messages
    tuiSyncTask.tuiDataSynchronization();

    // check all messages were successfully sent to TUI backend
    assertTrue(tuiPositionDao.findByPositionId(position.getId()).isEmpty());
  }

  private Campaigns createCampaign() throws Exception {
    Configuration configuration = new Configuration();
    Random random = new Random();
    configuration.setEndpointId("endpoint-" + random.nextInt());
    configuration.setClientId("client-" + random.nextInt());
    configuration.setOrchestratorId("orchestrator-" + random.nextInt());
    configuration.setAgvId(user.getKeycloakId());

    PostCampaignRequest postRequest = new PostCampaignRequest();
    postRequest.setName(UUID.randomUUID().toString());
    postRequest.setState(CampaignState.RUNNING);
    postRequest.setConfigurations(List.of(configuration));
    postRequest.setSiteId(site.getId());

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.Campaigns.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, Campaigns.class);
  }

  private Campaigns completeCampaign(String id) throws Exception {
    PatchCampaignRequest patchRequest = new PatchCampaignRequest();
    patchRequest.setName(UUID.randomUUID().toString());
    patchRequest.setState(CampaignState.COMPLETED);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.patch(Endpoints.Campaigns.PATH + Endpoints.Campaigns.ID, id)
            .with(user.get()).content(om.writeValueAsString(patchRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, Campaigns.class);
  }
}
