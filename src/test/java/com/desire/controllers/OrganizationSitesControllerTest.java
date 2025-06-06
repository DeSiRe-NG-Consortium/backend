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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PostOrPatchSiteRequest;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.utils.BaseMvcTest;
import com.desire.utils.FakeUser;
import com.desire.utils.TestDataService;

class OrganizationSitesControllerTest extends BaseMvcTest {

  @Autowired
  private TestDataService testDataService;

  private Organizations organization;

  private FakeUser user;

  @BeforeEach
  void setup() {
    organization = testDataService.getOrCreateOrganization("Test Org.");

    user = FakeUser.builder().organizationId(organization.getId()).roles(List.of(Roles.MANAGER))
        .build();
  }

  @Test
  void testCrud() throws Exception {
    PostOrPatchSiteRequest request = new PostOrPatchSiteRequest();
    request.setName("Test site.");
    request.setAvailable(true);

    // POST
    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders
            .post(Endpoints.Organizations.Sites.PATH, organization.getId()).with(user.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    Sites site = parse(result, Sites.class);

    assertNotNull(site);

    // GET all
    result = mockMvc
        .perform(MockMvcRequestBuilders
            .get(Endpoints.Organizations.Sites.PATH, organization.getId()).with(user.get())
            .content(om.writeValueAsString(request)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    List<Sites> sites = parseSearchList(result, Sites.class);

    assertFalse(sites.isEmpty());
    assertTrue(sites.contains(site));

    // GET by ID
    result = mockMvc
        .perform(MockMvcRequestBuilders
            .get(Endpoints.Organizations.Sites.PATH + Endpoints.Organizations.Sites.ID,
                organization.getId(), site.getId())
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    site = parse(result, Sites.class);

    assertNotNull(site);

    // PATCH
    request.setAvailable(false);

    result = mockMvc
        .perform(MockMvcRequestBuilders
            .patch(Endpoints.Organizations.Sites.PATH + Endpoints.Organizations.Sites.ID,
                organization.getId(), site.getId())
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    site = parse(result, Sites.class);

    assertNotNull(site);
    assertFalse(site.isAvailable());

    // DELETE
    mockMvc
        .perform(MockMvcRequestBuilders
            .delete(Endpoints.Organizations.Sites.PATH + Endpoints.Organizations.Sites.ID,
                organization.getId(), site.getId())
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk());

    // Verify delete
    mockMvc
        .perform(MockMvcRequestBuilders
            .get(Endpoints.Organizations.Sites.PATH + Endpoints.Organizations.Sites.ID,
                organization.getId(), site.getId())
            .with(user.get()).content(om.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$").doesNotExist());
  }
}
