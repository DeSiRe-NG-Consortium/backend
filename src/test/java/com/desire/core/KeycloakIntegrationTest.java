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

package com.desire.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.desire.utils.BaseMvcTest;
import jakarta.ws.rs.core.Response;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakIntegrationTest extends BaseMvcTest {

  /*
   * Example to fake user data from Keycloak
   * 
   * @WithMockKeycloakAuth(authorities = Roles.WithPrefix.USER, claims
   * = @OpenIdClaims(preferredUsername = "USER_ID"))
   */

  @Autowired
  RealmResource keycloakClient;

  private static final String FAKE_USER_NAME = "FAKE_TEST_USER";

  @Test
  @Order(1)
  void createUserTest() {
    UserRepresentation newUser = new UserRepresentation();
    newUser.setUsername(FAKE_USER_NAME);
    newUser.setEmail("user@domain.com");
    newUser.setCredentials(this.buildCredentials("PASSWORD_EXAMPLE"));
    newUser.setEnabled(true);

    try (Response response = keycloakClient.users().create(newUser)) {
      assertEquals(response.getStatus(), HttpStatus.CREATED.value(), "Test user creation error");

      List<UserRepresentation> users = keycloakClient.users().search(FAKE_USER_NAME);

      assertTrue(CollectionUtil.isNotEmpty(users), "No user found after creation");
    }
  }

  @Test
  @Order(3)
  void deleteUserTest() {
    List<UserRepresentation> users = keycloakClient.users().search(FAKE_USER_NAME);

    assertTrue(CollectionUtil.isNotEmpty(users), "No users found, cannot delete user");

    // Deletes the test user from Keycloak
    keycloakClient.users().delete(users.get(0).getId());

    // Make sure user was removed
    users = keycloakClient.users().search(FAKE_USER_NAME);
    assertTrue(users.isEmpty(), "Error trying to delete the user");
  }

  /**
   * Builds Credential object needed to set up keycloak's user password
   * 
   * @param password plain text password
   * @return Credential object for keycloak's call
   */
  private List<CredentialRepresentation> buildCredentials(String password) {
    CredentialRepresentation credentials = new CredentialRepresentation();
    credentials.setTemporary(false);
    credentials.setType(CredentialRepresentation.PASSWORD);
    credentials.setValue(password);

    return List.of(credentials);
  }

}
