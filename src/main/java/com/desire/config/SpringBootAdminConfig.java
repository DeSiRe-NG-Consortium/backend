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

import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpHeaders;
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * Actuator endpoints are protected with TECHNICAL_ADMIN role so Spring Boot Admin needs
 * authorization to be able to request information from Actuator endpoints.
 * </p>
 *
 * <p>
 * To solve this we provide a custom implementation of Spring Boot Admin's HttpHeadersProvider in
 * order to add Keycloak's admin client's token which holds Service Account's permissions.
 * </p>
 *
 * @see <a href="http://docs.spring-boot-admin.com/2.1.5/#customizing-headers">6.2. Injecting Custom
 *      HTTP Headers: HttpHeadersProvider implementation example</a>
 */
@Configuration
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SpringBootAdminConfig {

  private final @NonNull Keycloak keycloak;

  /**
   * Adds Keycloak's Service Account token from KC Admin client to Spring Boot Admin's headers.
   */
  @Bean
  @DependsOn("keycloak")
  public HttpHeadersProvider customHttpHeadersProvider(Keycloak keycloak) {
    return instance -> {
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.add("Authorization", "Bearer " + keycloak.tokenManager().getAccessTokenString());
      return httpHeaders;
    };
  }

}
