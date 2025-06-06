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

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class KeycloakClientConfig {

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${keycloak.auth-server-url}")
  String keycloakServerUrl;

  @Value("${keycloak.credentials.secret}")
  String kcSecret;

  /**
   * The Keycloak Admin client that provides the service-account Access-Token
   *
   * @return
   */
  @Bean
  public Keycloak keycloak() {
    return KeycloakBuilder.builder().serverUrl(keycloakServerUrl).realm(realm)
        .grantType(OAuth2Constants.CLIENT_CREDENTIALS).clientId(clientId).clientSecret(kcSecret)
        .build();
  }

  @Bean
  @DependsOn("keycloak")
  public RealmResource keycloakClient() {
    return keycloak().realm(realm);
  }

}
