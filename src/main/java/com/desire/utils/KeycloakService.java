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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import com.desire.constants.Roles;
import com.desire.utils.Constants.UserAttributes;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class KeycloakService {

  private final @NonNull RealmResource keycloak;

  /**
   * Returns UserRepresentation by username
   *
   * @param username
   * @return null if no user found
   */
  public Optional<UserRepresentation> getUserByUsername(String username) {
    try {
      List<UserRepresentation> userList = keycloak.users().search(username);

      if (CollectionUtils.isEmpty(userList)) {
        return Optional.empty();
      }
      return Optional.of(userList.get(0));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * Returns UserRepresentation by user ID
   *
   * @param id
   * @return null if no user found
   */
  public Optional<UserRepresentation> getUserById(String id) {
    try {
      return Optional.of(keycloak.users().get(id).toRepresentation());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public UserResource getUserResourceById(String keycloakId) {
    try {
      return keycloak.users().get(keycloakId);
    } catch (Exception e) {
      return null;
    }
  }

  public List<String> getOrganizationIds(String userId) {
    Optional<UserRepresentation> userOptional = getUserById(userId);

    if (userOptional.isPresent()) {
      List<String> organizations =
          userOptional.get().getAttributes().get(Constants.UserAttributes.ORGANIZATION_ID);

      if (!CollectionUtils.isEmpty(organizations)) {
        return organizations;
      }
    }

    return List.of();
  }

  public void resetPassword(String userId) {
    if (StringUtils.isNotBlank(userId)) {
      try {
        keycloak.users().get(userId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
      } catch (Exception e) {
        log.error("Error resetting password");
      }
    }
  }

  /**
   * Adding role to Keycloak user
   *
   * @param id
   * @param roles
   */
  public void addRole(String id, String... roles) {
    UserResource userResource = keycloak.users().get(id);

    // Add role to the user
    userResource.roles().realmLevel().add(getApplicationRoles(roles));
  }

  /**
   * Returns keycloak's role representations
   *
   * @param roles List of roles to return
   * @return List of keycloak's roles representations
   */
  public List<RoleRepresentation> getApplicationRoles(String... roles) {
    List<RoleRepresentation> rolesList = new ArrayList<>();
    for (String roleName : roles) {
      rolesList.add(keycloak.roles().get(roleName).toRepresentation());
    }
    return rolesList;
  }

  /**
   * Overwrites any keycloak's user attribute
   *
   * @param user User representation
   * @param key Attribute name
   * @param values Attribute values to set
   */
  public void setAttribute(UserRepresentation user, String key, List<String> values) {
    user.getAttributes().put(key, values);

    keycloak.users().get(user.getId()).update(user);
  }

  /**
   * Returns values of an attribute key/name
   *
   * @param user
   * @param key Attribute name to return
   * @return List of values
   */
  public String getAttribute(UserRepresentation user, String key) {
    List<String> attributes = user.getAttributes().get(key);

    if (!CollectionUtils.isEmpty(attributes)) {
      return attributes.get(0);
    }

    return null;
  }

  /**
   * Returns values of an attribute key/name
   *
   * @param user
   * @param key Attribute name to return
   * @return List of values
   */
  public List<String> getAttributeList(UserRepresentation user, String key) {
    return user.getAttributes().get(key);
  }

  /**
   * Returns user assigned realm roles
   *
   * @param userId
   * @return
   */
  public List<String> getRoles(String userId) {
    if (StringUtils.isBlank(userId)) {
      return Collections.emptyList();
    }

    try {
      MappingsRepresentation roleMappings = keycloak.users().get(userId).roles().getAll();

      List<String> userRoles =
          roleMappings.getRealmMappings().stream().map(RoleRepresentation::getName).toList();

      return userRoles.stream().filter(Roles.LIST::contains).toList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  public List<UserRepresentation> search(String queryString, String role) {
    List<UserRepresentation> users =
        keycloak.users().searchByAttributes(null, Integer.MAX_VALUE, true, null, queryString);

    if (StringUtils.isNotBlank(role)) {
      // Retrieve the role object from Keycloak
      RoleRepresentation kcRole = keycloak.roles().get(role).toRepresentation();

      if (kcRole != null && !CollectionUtils.isEmpty(users)) {
        return users.stream().filter(user -> getUserResourceById(user.getId()).roles().realmLevel()
            .listAll().contains(kcRole)).toList();
      }
    }

    return users;
  }

  public List<UserRepresentation> search(String role, String organizationId, String siteId) {
    // Build the query for the attribute search
    HashMap<String, String> attributes = new HashMap<>();

    if (StringUtils.isNotBlank(organizationId)) {
      attributes.put(UserAttributes.ORGANIZATION_ID, organizationId);
    }

    if (StringUtils.isNotBlank(siteId)) {
      attributes.put(UserAttributes.SITE_ID, siteId);
    }

    String queryString = buildAttributeQueryString(attributes);

    return search(queryString, role);
  }

  private static String buildAttributeQueryString(HashMap<String, String> attributes) {
    List<String> attributesList = new ArrayList<>();

    for (var entry : attributes.entrySet()) {
      attributesList.add(StringUtils.joinWith(":", entry.getKey(), entry.getValue()));
    }

    return String.join(" ", attributesList);
  }
}
