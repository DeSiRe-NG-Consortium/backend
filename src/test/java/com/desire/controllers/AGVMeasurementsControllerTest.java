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

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.desire.constants.Roles;
import com.desire.dtos.PostAGVMeasurementRequest;
import com.desire.dtos.PostAGVMeasurementRequest.PostAGVMeasurementValueRequest;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Organizations;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.validations.codes.SystemErrorCodes;
import java.time.ZonedDateTime;
import java.util.List;
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

class AGVMeasurementsControllerTest extends BaseMvcTest {

  private FakeUser user;
  private Campaigns campaign;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");
    user = FakeUser.builder().organizationId(organization.getId()).roles(List.of(Roles.ADMIN))
        .build();

    Configuration configuration = new Configuration();
    configuration.setOrchestratorId("orchestrator-1");
    configuration.setEndpointId("endpoint-1");
    configuration.setClientId("client-1");
    configuration.setAgvId(UUID.randomUUID().toString());

    campaign = testDataService.createCampaign("TEST", CampaignState.RUNNING,
        testDataService.getOrCreateSite("Test site", organization), configuration);
  }

  @Test
  void testPostMeasurementsSuccess() throws Exception {
    MvcResult result = mockMvc.perform(
            MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
                .with(user.get()).content(om.writeValueAsString(createRequest()))
                .contentType(MediaType.APPLICATION_JSON)).andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

    List<AGVMeasurements> measurements = parseList(result, AGVMeasurements.class);

    assertFalse(measurements.isEmpty());
  }

  @Test
  void testPostMeasurementsSuccessRawData() throws Exception {
    String requestString = """
        {
            "campaignId": "$CAMPAIGN",
            "values": [
              {
                "agv_id": "$AGV",
                "campaign_id": "$CAMPAIGN",
                "client_id": "$CLIENT",
                "datarate": 199,
                "endpoint_id": "$ENDPOINT",
                "latency": 16.9,
                "locationX": -1,
                "locationY": -1,
                "locationZ": -1,
                "measurementId": 11465,
                "time": "Thu, 20 Feb 2025 13:32:52 GMT"
              },
              {
                "agv_id": "$AGV",
                "campaign_id": "$CAMPAIGN",
                "client_id": "$CLIENT",
                "datarate": 200,
                "endpoint_id": "$ENDPOINT",
                "latency": 17.8,
                "locationX": -1,
                "locationY": -1,
                "locationZ": -1,
                "measurementId": 11466,
                "time": "Thu, 20 Feb 2025 13:32:53 GMT"
              }
            ]
          }
        """;

    requestString = StringUtils.replace(requestString, "$AGV",
        campaign.getConfigurations().get(0).getAgvId());
    requestString = StringUtils.replace(requestString, "$CAMPAIGN", campaign.getId());
    requestString = StringUtils.replace(requestString, "$CLIENT",
        campaign.getConfigurations().get(0).getClientId());
    requestString = StringUtils.replace(requestString, "$ENDPOINT",
        campaign.getConfigurations().get(0).getEndpointId());

    MvcResult result = mockMvc.perform(
            MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
                .with(user.get()).content(requestString).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<AGVMeasurements> measurements = parseList(result, AGVMeasurements.class);

    assertFalse(measurements.isEmpty());
  }

  @Test
  void testPostMeasurementsFailDuplicateData() throws Exception {
    PostAGVMeasurementRequest request = createRequest();

    MvcResult result = mockMvc.perform(
            MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
                .with(user.get()).content(om.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)).andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

    List<AGVMeasurements> measurements = parseList(result, AGVMeasurements.class);

    assertFalse(measurements.isEmpty());

    mockMvc.perform(MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON)).andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1))).andExpect(
            MockMvcResultMatchers.jsonPath("$.errors[*].code")
                .value(Matchers.hasItem(SystemErrorCodes.VALIDATION_ERROR.toString())));
  }

  @Test
  void testPostMeasurementsFailInvalidCampaign() throws Exception {
    PostAGVMeasurementRequest request = createRequest();

    request.setCampaignId(UUID.randomUUID().toString());

    mockMvc.perform(MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON)).andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(1))).andExpect(
            MockMvcResultMatchers.jsonPath("$.errors[*].code")
                .value(Matchers.hasItem(SystemErrorCodes.RESOURCE_NOT_FOUND.toString())));
  }

  @Test
  void testPostMeasurementsFailDataMismatch() throws Exception {
    PostAGVMeasurementRequest request = createRequest();

    request.getValues().get(0).setCampaignId(UUID.randomUUID().toString());
    request.getValues().get(0).setAgvId(UUID.randomUUID().toString());
    request.getValues().get(0).setEndpointId(UUID.randomUUID().toString());
    request.getValues().get(0).setClientId(UUID.randomUUID().toString());

    mockMvc.perform(MockMvcRequestBuilders.post(com.desire.constants.Endpoints.AGVMeasurements.PATH)
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON)).andDo(MockMvcResultHandlers.print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest())
        .andExpect(MockMvcResultMatchers.jsonPath("$.errors", Matchers.hasSize(4))).andExpect(
            MockMvcResultMatchers.jsonPath("$.errors[*].code")
                .value(Matchers.hasItem(SystemErrorCodes.VALIDATION_ERROR.toString())));
  }

  private PostAGVMeasurementRequest createRequest() {
    PostAGVMeasurementRequest request = new PostAGVMeasurementRequest();
    request.setCampaignId(campaign.getId());

    PostAGVMeasurementValueRequest valueRequest = new PostAGVMeasurementValueRequest();
    valueRequest.setMeasurementId(1L);
    valueRequest.setAgvId(campaign.getConfigurations().get(0).getAgvId());
    valueRequest.setClientId(campaign.getConfigurations().get(0).getClientId());
    valueRequest.setEndpointId(campaign.getConfigurations().get(0).getEndpointId());
    valueRequest.setCampaignId(campaign.getId());
    valueRequest.setMeasureMoment(ZonedDateTime.now());
    valueRequest.setLatency(5f);
    valueRequest.setDataRate(50f);
    valueRequest.setLocationX(1f);
    valueRequest.setLocationY(2f);
    valueRequest.setLocationZ(3f);

    request.setValues(List.of(valueRequest));

    return request;
  }
}
