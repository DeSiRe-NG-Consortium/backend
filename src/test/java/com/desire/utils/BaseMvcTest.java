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

package com.desire.utils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.CollectionUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.desire.AsyncTestConfig;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGVs.StatusEvents;
import com.desire.dtos.PostAGVPositionRequest;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import com.desire.types.AGVStatusEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

/**
 * Base class for MVC-based tests with support for mocks.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ContextConfiguration(classes = AsyncTestConfig.class)
@DirtiesContext
public abstract class BaseMvcTest {

  @Container
  @ServiceConnection
  static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper om;

  @Autowired
  protected TestDataService testDataService;

  @MockitoSpyBean
  protected KeycloakService keycloakService;

  /**
   * Parses a MvcResult into a java object bean
   *
   * @param <T>
   * @param result
   * @param targetClass
   * @return
   * @throws Exception
   */
  public <T> T parse(MvcResult result, Class<T> targetClass) throws Exception {
    String jsonResponse = result.getResponse().getContentAsString();

    return om.readValue(jsonResponse, targetClass);
  }

  /**
   * Parses a MvcResult into a java object bean list
   *
   * @param <T>
   * @param result
   * @param targetClass
   * @return Parsed objects as list.
   * @throws Exception
   */
  public <T> List<T> parseList(MvcResult result, Class<T> targetClass) throws Exception {
    String jsonResponse = result.getResponse().getContentAsString();

    JsonNode rootNode = om.readTree(jsonResponse);
    // In spring native, values array is withing 'content' node
    if (rootNode.has("content")) {
      jsonResponse = rootNode.get("content").toString();
    }

    CollectionType listType =
        om.getTypeFactory().constructCollectionType(ArrayList.class, targetClass);

    return om.readValue(jsonResponse, listType);
  }

  /**
   * Parses a MvcResult (coming from a search GET endpoint that retunrs PAge<Entity>) into a java
   * object bean list
   *
   * @param <T>
   * @param result
   * @param targetClass
   * @return Parsed objects as list.
   * @throws Exception
   */
  public <T> List<T> parseSearchList(MvcResult result, Class<T> targetClass) throws Exception {
    JsonNode rootNode = om.readTree(result.getResponse().getContentAsString());
    JsonNode contentNode = rootNode.path("content");

    CollectionType listType =
        om.getTypeFactory().constructCollectionType(ArrayList.class, targetClass);

    return om.readValue(contentNode.toString(), listType);
  }

  public <T> List<T> parseFromStream(MvcResult result, Class<T> targetClass) throws Exception {
    String jsonStreamResponse = result.getResponse().getContentAsString();

    if (jsonStreamResponse.contains("}\n{")) {
      // make it a valid JSON list
      jsonStreamResponse = "[" + StringUtils.replace(jsonStreamResponse, "}\n{", "},{") + "]";

      CollectionType listType = om.getTypeFactory()
          .constructCollectionType(ArrayList.class, targetClass);

      return om.readValue(jsonStreamResponse, listType);
    } else {
      return List.of(om.readValue(jsonStreamResponse, targetClass));
    }
  }

  protected Optional<UserRepresentation> getFakeUserRepresentation(String userId,
      String organizationId, String siteId) {
    UserRepresentation user = new UserRepresentation();

    Map<String, List<String>> attributes = new HashMap<>();
    attributes.put(Constants.UserAttributes.ORGANIZATION_ID, List.of(organizationId));

    if (StringUtils.isNotBlank(siteId)) {
      attributes.put(Constants.UserAttributes.SITE_ID, List.of(siteId));
    }

    user.setAttributes(attributes);
    user.setId(userId);

    return Optional.of(user);
  }

  protected Optional<UserRepresentation> getFakeUserRepresentation(Organizations organization,
      Sites site) {
    return getFakeUserRepresentation(UUID.randomUUID().toString(),
        organization != null ? organization.getId() : null, site != null ? site.getId() : null);
  }

  protected Optional<UserRepresentation> getFakeUserRepresentation(String organizationId,
      String siteId) {
    return getFakeUserRepresentation(UUID.randomUUID().toString(), organizationId, siteId);
  }

  protected void mockKeycloakUser(String userId, Organizations organization, Sites site,
      List<String> roles) {
    Mockito.when(keycloakService.getUserById(userId))
        .thenReturn(getFakeUserRepresentation(organization, site));
    Mockito.when(keycloakService.getOrganizationIds(userId))
        .thenReturn(List.of(organization.getId()));
    Mockito.when(keycloakService.getRoles(userId)).thenReturn(roles);
  }

  protected void mockKeycloakUser(FakeUser user) {
    Mockito.when(keycloakService.getUserById(user.getKeycloakId()))
        .thenReturn(getFakeUserRepresentation(user.getOrganizationId(), user.siteId));
    Mockito.when(keycloakService.getOrganizationIds(user.getOrganizationId()))
        .thenReturn(List.of(user.getOrganizationId()));
    Mockito.when(keycloakService.getRoles(user.getKeycloakId())).thenReturn(user.getRoles());
  }

  protected AGVPositions postAgvPosition(Campaigns campaign, List<LocalDateTime> measureMoments,
      FakeUser agvUser) throws Exception {
    PostAGVPositionRequest postRequest = new PostAGVPositionRequest();
    postRequest.setCampaignId(campaign.getId());
    if (!CollectionUtils.isEmpty(measureMoments)) {
      postRequest.setCoordinates(measureMoments.stream()
          .map(measureMoment -> testDataService.getRandomCoordinates(measureMoment)).toList());
    } else {
      postRequest.setCoordinates(List.of(testDataService.getRandomCoordinates(null)));
    }

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(Endpoints.AGVPositions.PATH).with(agvUser.get())
            .content(om.writeValueAsString(postRequest)).contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, AGVPositions.class);
  }

  protected AGVPositions postAgvPosition(Campaigns campaign, FakeUser agvUser) throws Exception {
    return postAgvPosition(campaign, null, agvUser);
  }

  protected AGVStatusEvents postAgvStatusEvent(AGVStatusEventType type, AGVCommands command,
      FakeUser agvUser) throws Exception {
    PostAgvStatusEventRequest postStatusEventRequest = new PostAgvStatusEventRequest();
    postStatusEventRequest.setMeasureMoment(LocalDateTime.now());
    postStatusEventRequest.setEventType(type);
    if (command != null) {
      postStatusEventRequest.setCommandId(command.getId());
    }

    MvcResult result = mockMvc
        .perform(MockMvcRequestBuilders.post(StatusEvents.PATH, agvUser.getKeycloakId())
            .with(agvUser.get()).content(om.writeValueAsString(postStatusEventRequest))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(MockMvcResultHandlers.print()).andExpect(MockMvcResultMatchers.status().isOk())
        .andReturn();

    return parse(result, AGVStatusEvents.class);
  }

  /**
   * Sets numeric fields with random values
   * 
   * @param obj
   * @throws Exception
   */
  public static void setRandomNumbers(Object obj) throws Exception {
    Class<?> clazz = obj.getClass();
    Field[] fields = clazz.getDeclaredFields();
    Random random = new Random();

    for (Field field : fields) {
      if (field.getType() == float.class || field.getType() == Float.class) {
        field.setAccessible(true);
        try {
          float randomValue = random.nextFloat();
          if (field.getType() == float.class) {
            field.setFloat(obj, randomValue);
          } else if (field.getType() == Float.class) {
            field.set(obj, randomValue);
          }
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      } else if (field.getType() == int.class) {
        field.setAccessible(true);
        field.setInt(obj, random.nextInt());
      }
    }
  }

}
