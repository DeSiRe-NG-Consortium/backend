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

import com.desire.constants.Endpoints.MobileMeasurements;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Roles;
import com.desire.dtos.PostMobileMeasurementRequest;
import com.desire.dtos.SearchMobileMeasurementRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Organizations;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;

class MobileMeasurementsControllerTest extends BaseMvcTest {

  private FakeUser user;
  private Campaigns campaign;

  @BeforeEach
  void setup() {
    Organizations organization = testDataService.getOrCreateOrganization("Test Org.");
    user = FakeUser.builder().organizationId(organization.getId())
        .roles(List.of(Roles.OPERATOR, Roles.COLLECTOR)).build();
    campaign = testDataService.getOrCreateCampaign("TEST", CampaignState.RUNNING,
        testDataService.getOrCreateSite("Test site", organization));
  }

  /**
   * Create measurement and retrieve it using search
   *
   * @throws Exception
   */
  @Test
  void testPostMeasurementsSuccess() throws Exception {
    PostMobileMeasurementRequest postRequest = new PostMobileMeasurementRequest();
    postRequest.setCampaignId(campaign.getId());
    postRequest.setMeasureMoment(LocalDateTime.now());
    setRandomNumbers(postRequest);

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(MobileMeasurements.PATH).with(user.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    com.desire.model.entities.MobileMeasurements measurement = parse(result, com.desire.model.entities.MobileMeasurements.class);

    assertNotNull(measurement);

    SearchMobileMeasurementRequest searchRequest = new SearchMobileMeasurementRequest();
    searchRequest.setCampaignId(campaign.getId());

    result = mockMvc
        .perform(MockMvcRequestBuilders.get(MobileMeasurements.PATH)
            .param(SearchMobileMeasurementRequest.Fields.campaignId, campaign.getId()).with(user.get())
            .content(om.writeValueAsString(searchRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<com.desire.model.entities.MobileMeasurements> measurements = parseList(result, com.desire.model.entities.MobileMeasurements.class);

    assertEquals(1, measurements.size());
  }

}
