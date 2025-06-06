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

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.desire.config.KeycloakRolesConfig.Jwt2AuthenticationConverter;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;

/**
 * Manages endpoints protections, CORS…
 */
@Configuration
@EnableMethodSecurity(jsr250Enabled = true) // Enables @RolesAllowed
public class SecurityConfig {

  @Value("${CORS_ALLOWED_ORIGINS:}")
  private String corsAllowedUrls;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http,
      Jwt2AuthenticationConverter authenticationConverter,
      OAuth2UserService<OAuth2UserRequest, OAuth2User> oidcUserService) throws Exception {

    http.cors(Customizer.withDefaults());
    http.csrf(AbstractHttpConfigurer::disable);

    http.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
        .requestMatchers(Endpoints.SPRING_ADMIN_REGISTRATION_URL).permitAll()
        .requestMatchers(Endpoints.SPRING_ADMIN).hasAuthority(Roles.TECHNICAL_ADMIN)
        .requestMatchers(Endpoints.ACTUATOR).hasAuthority(Roles.TECHNICAL_ADMIN)
        .requestMatchers(Endpoints.SWAGGER_UI).hasAuthority(Roles.TECHNICAL_USER).anyRequest()
        .permitAll()).httpBasic(Customizer.withDefaults());

    // Configures JWT roles converter
    http.oauth2ResourceServer(
        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));

    // Enables Keycloak's web session
    http.oauth2Login(
        oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userService(oidcUserService)));

    return http.build();
  }

  @Bean
  CorsFilter corsFilter() {
    CorsConfiguration corsConfiguration = new CorsConfiguration();

    corsConfiguration.setAllowedOriginPatterns(Arrays.asList(corsAllowedUrls.split(",")));
    corsConfiguration.addAllowedMethod("*");
    corsConfiguration.addAllowedHeader("*");
    corsConfiguration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfiguration);

    return new CorsFilter(source);
  }

}
