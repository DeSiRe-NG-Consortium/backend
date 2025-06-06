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
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;

/**
 * Builder to create a fake user representation for testing purposes
 */
@Builder
@Getter
public class FakeUser {

  private static final String TOKEN_TYPE = "id-token";
  private static final String NAME_ATTRIBUTE_KEY = "user_name";

  @Builder.Default
  String username = "default_fake_username";

  @NotEmpty
  @Builder.Default
  List<String> roles = new ArrayList<>();

  String organizationId;

  String siteId;

  @Builder.Default
  String keycloakId = UUID.randomUUID().toString();

  /**
   * Returns an OIDC fake user to request endpoints
   */
  public OidcLoginRequestPostProcessor get() {
    org.springframework.security.oauth2.core.oidc.OidcIdToken.Builder tokenBuilder =
        OidcIdToken.withTokenValue(TOKEN_TYPE);

    tokenBuilder.claim(NAME_ATTRIBUTE_KEY, username);

    tokenBuilder.claim(StandardClaimNames.SUB, keycloakId);

    List<GrantedAuthority> rolesAuthorities =
        AuthorityUtils.createAuthorityList(roles.toArray(String[]::new));

    if (organizationId != null) {
      tokenBuilder.claim(Constants.UserAttributes.ORGANIZATION_ID, organizationId);
    }

    if (siteId != null) {
      tokenBuilder.claim(Constants.UserAttributes.SITE_ID, siteId);
    }

    return SecurityMockMvcRequestPostProcessors.oidcLogin()
        .oidcUser(new DefaultOidcUser(rolesAuthorities, tokenBuilder.build(), NAME_ATTRIBUTE_KEY));
  }

}
