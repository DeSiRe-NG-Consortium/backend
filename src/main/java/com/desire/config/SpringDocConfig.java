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

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import jakarta.annotation.security.RolesAllowed;

/**
 * <p>
 * This class purpose is to generate a dynamic swagger description for each controller marking which
 * roles are allowed to use such endpoint.
 * </p>
 *
 * <p>
 * In case no roles were specified then endpoint is considered public.
 * </p>
 */
@OpenAPIDefinition(
    info = @Info(title = "${app.name}", version = "${git.commit.id.abbrev}@${app.buildTimestamp}"))
@Configuration
public class SpringDocConfig {

  @Bean
  OperationCustomizer operationCustomizer() {
    return (operation, handlerMethod) -> {
      // Get all roles allowed by reflection
      Optional<RolesAllowed> preAuthorizeAnnotation =
          Optional.ofNullable(handlerMethod.getMethodAnnotation(RolesAllowed.class));
      StringBuilder sb = new StringBuilder();

      if (preAuthorizeAnnotation.isPresent()) {
        String[] roles = (preAuthorizeAnnotation.get()).value();
        sb.append("Roles allowed to use this endpoint: **").append(String.join(", ", roles))
            .append("**");
      } else {
        sb.append("This endpoint is **PUBLIC**.");
      }

      // Show operation description afterwards if it is not empty
      if (StringUtils.isNotBlank(operation.getDescription())) {
        sb.append("<br /><br />");
        sb.append(operation.getDescription());
      }

      // Finally add all the info to the operation description
      operation.setDescription(sb.toString());

      return operation;
    };
  }

}
