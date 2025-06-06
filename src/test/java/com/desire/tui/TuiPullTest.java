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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.desire.constants.Roles;
import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.PostAGVMeasurementRequest.PostAGVMeasurementValueRequest;
import com.desire.events.AGVStreamService;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.AGVMeasurementRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.services.AGVMeasurementService;
import com.desire.tasks.TuiDataPullTask;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;

class TuiPullTest extends BaseMvcTest {

  @Autowired
  private AGVMeasurementRepository agvMeasurementDao;

  @Autowired
  private CampaignRepository campaignDao;

  @Autowired
  private TuiDataPullTask pullTask;

  @MockitoSpyBean
  private AGVStreamService streamService;

  @MockitoBean
  private RestTemplate restTemplate;

  private final List<Campaigns> campaigns = new ArrayList<>();

  private AutoCloseable mockitoAnnotations;

  private long measurementId = 1L;

  @BeforeEach
  void setup() {
    agvMeasurementDao.deleteAll();
    campaignDao.deleteAll();

    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");

    Sites site = testDataService.getOrCreateSite("My site", organization);

    FakeUser user = FakeUser.builder().organizationId(organization.getId()).siteId(site.getId())
        .keycloakId(UUID.randomUUID().toString()).roles(List.of(Roles.OPERATOR, Roles.AGV)).build();

    testDataService.setAgvOnline(user.getKeycloakId());

    mockKeycloakUser(user.getKeycloakId(), organization, site, user.getRoles());

    Configuration configuration = new Configuration();
    configuration.setOrchestratorId("orchestrator-1");
    configuration.setClientId("client-1");
    configuration.setEndpointId("endpoint-1");
    configuration.setAgvId(user.getKeycloakId());

    Campaigns campaign = testDataService.createCampaign("TUI Pull Campaign",
        CampaignState.RUNNING, site,
        configuration);
    campaign.setUpdateMoment(LocalDateTime.now().minusMinutes(50));
    campaigns.add(campaignDao.save(campaign));

    // Old campaign should not be synced.
    Campaigns otherCampaign = testDataService.getOrCreateCampaign("Old Campaign",
        CampaignState.COMPLETED,
        campaign.getSite());
    otherCampaign.setUpdateMoment(LocalDateTime.now().minusDays(1));
    otherCampaign.setConfigurations(List.of(configuration));
    campaigns.add(campaignDao.save(otherCampaign));

    mockitoAnnotations = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void releaseMocks() throws Exception {
    mockitoAnnotations.close();
  }

  @Test
  void testOfflineSync() {
    List<PostAGVMeasurementValueRequest> responseData1 = createResponseData(campaigns.get(0));
    List<PostAGVMeasurementValueRequest> responseData2 = createResponseData(campaigns.get(1));

    when(restTemplate.exchange(
        Mockito.contains(campaigns.get(0).getId()),
        eq(HttpMethod.GET), eq(HttpEntity.EMPTY),
        eq(AGVMeasurementService.REQUEST_TYPE))).thenReturn(
        new ResponseEntity<>(responseData1, HttpStatus.OK));
    when(restTemplate.exchange(
        Mockito.contains(campaigns.get(1).getId()),
        eq(HttpMethod.GET), eq(HttpEntity.EMPTY),
        eq(AGVMeasurementService.REQUEST_TYPE))).thenReturn(
        new ResponseEntity<>(responseData2, HttpStatus.OK));

    pullTask.offlinePull();

    Mockito.verify(restTemplate, Mockito.times(2))
        .exchange(anyString(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY),
            eq(AGVMeasurementService.REQUEST_TYPE));

    Mockito.verify(streamService, Mockito.times(2))
        .publish(Mockito.argThat(
                campaign -> campaign.equals(campaigns.get(0).getConfigurations().get(0).getAgvId())
                    || campaign.equals(campaigns.get(1).getConfigurations().get(0).getAgvId())),
            Mockito.any());

    List<AGVMeasurements> measurements = agvMeasurementDao.findAll();
    assertEquals(responseData1.size() + responseData2.size(), measurements.size());
    assertTrue(measurements.stream().anyMatch(
        measurement -> measurement.getCampaign().getId().equals(campaigns.get(0).getId())
            && responseData1.stream().map(PostAGVMeasurementValueRequest::getMeasurementId)
            .anyMatch(measurementId1 -> measurementId1 == measurement.getMeasurementId())));
    assertTrue(measurements.stream().anyMatch(
        measurement -> measurement.getCampaign().getId().equals(campaigns.get(1).getId())
            && responseData2.stream().map(PostAGVMeasurementValueRequest::getMeasurementId)
            .anyMatch(measurementId2 -> measurementId2 == measurement.getMeasurementId())));
  }

  @Test
  void testOnlineSync() {
    List<PostAGVMeasurementValueRequest> responseData = createResponseData(campaigns.get(0));

    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY),
        eq(AGVMeasurementService.REQUEST_TYPE))).thenReturn(
        new ResponseEntity<>(responseData, HttpStatus.OK));

    ArgumentCaptor<AGVUpdateDto> updateCaptor = ArgumentCaptor.forClass(AGVUpdateDto.class);

    pullTask.onlinePull();

    Mockito.verify(restTemplate, Mockito.times(1))
        .exchange(anyString(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY),
            eq(AGVMeasurementService.REQUEST_TYPE));

    Mockito.verify(streamService, Mockito.times(1))
        .publish(Mockito.eq(campaigns.get(0).getConfigurations().get(0).getAgvId()),
            updateCaptor.capture());

    AGVUpdateDto agvUpdate = updateCaptor.getValue();
    assertEquals(responseData.size(), agvUpdate.getMeasurements().size());

    List<AGVMeasurements> measurements = agvMeasurementDao.findAll();
    assertEquals(responseData.size(), measurements.size());
    assertTrue(measurements.stream().anyMatch(
        measurement -> measurement.getMeasurementId() == responseData.get(0).getMeasurementId()));
    assertTrue(measurements.stream().anyMatch(
        measurement -> measurement.getMeasurementId() == responseData.get(1).getMeasurementId()));
    assertTrue(measurements.stream().anyMatch(
        measurement -> measurement.getMeasurementId() == responseData.get(2).getMeasurementId()));
  }

  private List<PostAGVMeasurementValueRequest> createResponseData(Campaigns campaign) {
    List<PostAGVMeasurementValueRequest> responseData = new ArrayList<>();

    responseData.add(createRequestData(campaign, measurementId++));
    responseData.add(createRequestData(campaign, measurementId++));
    responseData.add(createRequestData(campaign, measurementId++));

    return responseData;
  }

  private PostAGVMeasurementValueRequest createRequestData(Campaigns campaign, long measurementId) {
    PostAGVMeasurementValueRequest request = new PostAGVMeasurementValueRequest();

    request.setAgvId(campaign.getConfigurations().get(0).getAgvId());
    request.setCampaignId(campaign.getId());
    request.setClientId(campaign.getConfigurations().get(0).getClientId());
    request.setEndpointId(campaign.getConfigurations().get(0).getEndpointId());
    request.setMeasurementId(measurementId);
    request.setMeasureMoment(ZonedDateTime.now());

    request.setLatency(RandomUtils.secure().randomFloat(0f, 100f));
    request.setDataRate(RandomUtils.secure().randomFloat(0f, 1000f));
    request.setLocationX(RandomUtils.secure().randomFloat(0f, 100f) - 50f);
    request.setLocationY(RandomUtils.secure().randomFloat(0f, 100f) - 50f);
    request.setLocationZ(RandomUtils.secure().randomFloat(0f, 100f) - 50f);

    return request;
  }
}
