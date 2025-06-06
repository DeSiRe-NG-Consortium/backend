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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGVs;
import com.desire.constants.Roles;
import com.desire.dtos.AGVDto;
import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.SearchAGVRequest.Fields;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandType;
import com.desire.types.AGVStatusEventType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.Pagination;
import com.desire.utils.TestDataService;

class AGVsControllerTest extends BaseMvcTest {

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private TestDataService testDataService;

  private FakeUser agvUser1;

  private FakeUser agvUser2;

  private FakeUser operatorUser;

  private Campaigns campaign;

  private Sites site;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");

    site = testDataService.getOrCreateSite("Test site", organization);

    agvUser1 = FakeUser.builder().organizationId(organization.getId()).siteId(site.getId())
        .keycloakId("28e475a5-02aa-47c3-919c-fd88f63e9300").roles(List.of(Roles.AGV)).build();
    agvUser2 = FakeUser.builder().organizationId(organization.getId())
        .keycloakId("400e97b3-ac56-4585-856b-d69c50f342a7").roles(List.of(Roles.AGV)).build();

    operatorUser = FakeUser.builder().organizationId(organization.getId())
        .roles(List.of(Roles.OPERATOR)).build();

    campaign = testDataService.getOrCreateCampaign("TEST-" + UUID.randomUUID(),
        CampaignState.RUNNING, site);

    Configuration configuration = new Configuration();
    configuration.setAgvId(agvUser1.getKeycloakId());
    campaign.setConfigurations(List.of(configuration));
    campaign.setState(CampaignState.RUNNING);
    campaign = campaignDao.save(campaign);

    mockKeycloakUser(agvUser1);
    mockKeycloakUser(agvUser2);

    Mockito.when(keycloakService.search(Roles.AGV, operatorUser.getOrganizationId(), null))
        .thenReturn(List.of(
            getFakeUserRepresentation(agvUser1.getKeycloakId(), agvUser1.getOrganizationId(),
                site.getId()).get(),
            getFakeUserRepresentation(agvUser2.getKeycloakId(), agvUser2.getOrganizationId(), null)
                .get()));
    Mockito.when(keycloakService.search(Roles.AGV, operatorUser.getOrganizationId(), site.getId()))
        .thenReturn(List.of(getFakeUserRepresentation(agvUser1.getKeycloakId(),
            agvUser1.getOrganizationId(), site.getId()).get()));
    Mockito.when(keycloakService.getUserById(agvUser1.getKeycloakId()))
        .thenReturn(getFakeUserRepresentation(agvUser1.getKeycloakId(),
            agvUser1.getOrganizationId(), site.getId()));

    testDataService.setAgvOnline(agvUser1.getKeycloakId());
    testDataService.createAGVStatusEvent(agvUser1.getKeycloakId(),
        AGVStatusEventType.COMPLETE_REQUEST, testDataService
            .createAGVCommand(agvUser1.getKeycloakId(), AGVCommandType.START_CAMPAIGN, campaign));
    testDataService.createAGVPosition(agvUser1.getKeycloakId(), campaign);

    testDataService.createAGVStatusEvent(agvUser2.getKeycloakId(), AGVStatusEventType.SHUTDOWN,
        null);

  }

  @Test
  void testGetDefault() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH).with(operatorUser.get())
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(2, agvs.size());

    // Validate sorting.
    assertEquals(agvUser2.getKeycloakId(), agvs.get(0).getId());

    // Validate AGV 2.
    assertFalse(agvs.get(0).isOnline());
    assertNull(agvs.get(0).getCurrentCampaign());
    assertEquals(0, agvs.get(0).getSites().size());
    assertEquals(AGVStatusEventType.SHUTDOWN, agvs.get(0).getLatestStatusEvent().getEventType());
    assertNull(agvs.get(0).getLatestPosition());

    // Validate AGV 1.
    assertTrue(agvs.get(1).isOnline());
    assertEquals(1, agvs.get(1).getSites().size());
    assertEquals(site.getId(), agvs.get(1).getSites().get(0).getId());
    assertEquals(campaign, agvs.get(1).getCurrentCampaign());
    assertEquals(AGVStatusEventType.COMPLETE_REQUEST,
        agvs.get(1).getLatestStatusEvent().getEventType());
    assertNotNull(agvs.get(1).getLatestPosition());
  }

  @Test
  void testGetParameterOnline() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Fields.online, Boolean.TRUE.toString()).with(operatorUser.get())
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser1.getKeycloakId(), agvs.get(0).getId());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Fields.online, Boolean.FALSE.toString()).with(operatorUser.get())
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser2.getKeycloakId(), agvs.get(0).getId());
  }

  @Test
  void testGetParameterIdle() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Fields.idle, Boolean.TRUE.toString()).with(operatorUser.get())
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser2.getKeycloakId(), agvs.get(0).getId());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Fields.idle, Boolean.FALSE.toString()).with(operatorUser.get())
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser1.getKeycloakId(), agvs.get(0).getId());
  }

  @Test
  void testGetParameterSite() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH).param(Fields.siteId, site.getId())
            .with(operatorUser.get()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser1.getKeycloakId(), agvs.get(0).getId());
  }

  @Test
  void testGetParameterSort() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Pagination.Fields.sortDirection, Direction.ASC.toString())
            .with(operatorUser.get()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(2, agvs.size());

    assertEquals(agvUser1.getKeycloakId(), agvs.get(0).getId());
  }

  @Test
  void testGetParameterPagination() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH)
            .param(Pagination.Fields.pageNumber, "1").param(Pagination.Fields.pageSize, "1")
            .with(operatorUser.get()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVDto> agvs = parseSearchList(result, AGVDto.class);

    assertEquals(1, agvs.size());

    assertEquals(agvUser1.getKeycloakId(), agvs.get(0).getId());
  }

  @Test
  void testGetById() throws Exception {
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.get(Endpoints.AGVs.PATH + AGVs.ID, agvUser1.getKeycloakId())
            .with(operatorUser.get()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(new Pagination())))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    AGVDto agv = parse(result, AGVDto.class);

    assertEquals(agvUser1.getKeycloakId(), agv.getId());
  }

  @Test
  void testGetStream() throws Exception {
    MvcResult result = mockMvc
        .perform(
            MockMvcRequestBuilders.get(Endpoints.AGVs.PATH + AGVs.STREAM, agvUser1.getKeycloakId())
                .with(operatorUser.get()))
        .andExpect(MockMvcResultMatchers.request().asyncStarted())
        .andExpect(MockMvcResultMatchers.status().isOk()).andDo(MockMvcResultHandlers.print())
        .andReturn();

    List<AGVUpdateDto> updates = parseFromStream(result, AGVUpdateDto.class);

    assertEquals(1, updates.size());

    assertNull(updates.get(0).getPosition());
    assertNull(updates.get(0).getStatus());
  }
}
