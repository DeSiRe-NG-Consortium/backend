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

package com.desire.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * <p>
 * The scope of his class is to configure mappers for both OAuth2 and JWT tokens to extract the
 * roles from both token types.
 * </p>
 *
 * <p>
 * By default Spring Security 6 will map the scopes as roles, also accessing the web via JWT or
 * OAuth2 (web browser) have independent implementations so they must be handled individually.
 * </p>
 *
 * @see <a href=
 *      "https://docs.spring.io/spring-security/site/docs/5.1.0.RELEASE/reference/htmlsingle/#oauth2login-advanced">docs.spring.io/spring-security/site/docs/5.1.0.RELEASE/reference/htmlsingle/#oauth2login-advanced</a>
 * @see <a href=
 *      "https://docs.spring.io/spring-security/reference/reactive/oauth2/login/advanced.html#webflux-oauth2-login-advanced-map-authorities-grantedauthoritiesmapper">docs.spring.io/spring-security/reference/reactive/oauth2/login/advanced.html#webflux-oauth2-login-advanced-map-authorities-grantedauthoritiesmapper</a>
 */
@Component
public class KeycloakRolesConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  String issuerUrl;

  @Value("${keycloak.resource}")
  String clientId;

  /**
   * Extracts roles from JWT (realm_access.roles)
   *
   * @return
   */
  @SuppressWarnings("unchecked")
  @Bean
  public Jwt2AuthoritiesConverter authoritiesConverter() {
    return jwt -> {
      final var realmAccess =
          (Map<String, Object>) jwt.getClaims().getOrDefault("realm_access", Map.of());
      final var realmRoles = (Collection<String>) realmAccess.getOrDefault("roles", List.of());

      return realmRoles.stream().map(SimpleGrantedAuthority::new).toList();
    };
  }

  /**
   * Creates user session
   *
   * @param converter
   * @return
   */
  @Bean
  @DependsOn("authoritiesConverter")
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> oidcUserService(
      Jwt2AuthoritiesConverter converter) {
    final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    return userRequest -> {
      OAuth2User oAuth2User = delegate.loadUser(userRequest);
      OAuth2AccessToken accessToken = userRequest.getAccessToken();
      Jwt jwt = JwtDecoders.fromIssuerLocation(issuerUrl).decode(accessToken.getTokenValue());

      Set<GrantedAuthority> mappedAuthorities = new HashSet<>(converter.convert(jwt));

      return new DefaultOAuth2User(mappedAuthorities, oAuth2User.getAttributes(),
          "preferred_username");
    };
  }

  /**
   * Removes "ROLE_" from default prefixes
   *
   * @return
   */
  @Bean
  static GrantedAuthorityDefaults grantedAuthorityDefaults() {
    return new GrantedAuthorityDefaults("");
  }

  @Bean
  public Jwt2AuthenticationConverter authenticationConverter(
      Jwt2AuthoritiesConverter authoritiesConverter) {
    return jwt -> new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt));
  }

  public interface Jwt2AuthoritiesConverter
      extends Converter<Jwt, Collection<? extends GrantedAuthority>> {

  }

  public interface Jwt2AuthenticationConverter extends Converter<Jwt, AbstractAuthenticationToken> {

  }

}
