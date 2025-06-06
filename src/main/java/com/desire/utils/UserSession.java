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

import java.util.Collections;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import com.desire.constants.Roles;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Representation of logged-in user information <br />
 * Takes all information from the requesting user Authorization Token
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserSession {

  /**
   * Return a list of logged-in user roles
   */
  public static List<String> roles() {
    try {
      Authentication auth = getAuthentication();

      // Filter the roles so only application roles are being shown
      return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
          .filter(Roles.LIST::contains).toList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  public static String id() {
    return getAttributeFromSession(StandardClaimNames.SUB);
  }

  public static String name() {
    return getAttributeFromSession(StandardClaimNames.GIVEN_NAME);
  }

  public static String lastName() {
    return getAttributeFromSession(StandardClaimNames.FAMILY_NAME);
  }

  public static String fullName() {
    return getAttributeFromSession(StandardClaimNames.NAME);
  }

  public static String email() {
    return getAttributeFromSession(StandardClaimNames.EMAIL);
  }

  public static boolean isAdmin() {
    return roles().contains(Roles.ADMIN);
  }

  public static boolean isOperator() {
    return roles().contains(Roles.OPERATOR);
  }

  public static boolean isAGV() {
    return roles().contains(Roles.AGV);
  }

  public static String organizationId() {
    return getAttributeFromSession(Constants.UserAttributes.ORGANIZATION_ID);
  }

  public static String siteId() {
    return getAttributeFromSession(Constants.UserAttributes.SITE_ID);
  }

  private static Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private static String getAttributeFromSession(String attribute) {
    // Retrieving logged in user data
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null) {
      return null;
    }

    /*
     * JWT comes from "Authentication: Bearer TOKEN" header. DefaultOAuth2User comes from web
     * browser sessions
     */
    if (auth.getPrincipal() instanceof Jwt jwt) {
      return jwt.getClaimAsString(attribute);
    } else if (auth.getPrincipal() instanceof DefaultOAuth2User user) {
      return user.getAttribute(attribute);
    }

    return null;
  }

  public static List<String> getAttributeListFromSession(String attribute) {
    // Retrieving logged in user data
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null) {
      return Collections.emptyList();
    }

    /**
     * JWT comes from "Authentication: Bearer TOKEN" header. DefaultOAuth2User comes from web
     * browser sessions
     */
    List<String> attributeList = null;
    if (auth.getPrincipal() instanceof Jwt jwt) {
      attributeList = jwt.getClaimAsStringList(attribute);
    } else if (auth.getPrincipal() instanceof DefaultOAuth2User user) {
      attributeList = user.getAttribute(attribute);
    }

    return attributeList != null ? attributeList : List.of();
  }

}
