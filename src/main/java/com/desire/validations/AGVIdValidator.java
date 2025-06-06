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

package com.desire.validations;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.desire.constants.Roles;
import com.desire.utils.KeycloakService;
import com.desire.utils.UserSession;
import com.desire.validations.annotations.AGVId;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVIdValidator implements ConstraintValidator<AGVId, String> {

  private final @NonNull KeycloakService keycloakService;

  /**
   * Checks if the AGV ID provided corresponds to a user with AGV role. Also, logged-in user's
   * organization ID must be contained in AGV user organization IDs.
   */
  @Override
  public boolean isValid(String agvId, ConstraintValidatorContext cxt) {
    if (StringUtils.isBlank(agvId)) {
      return true;
    }

    try {
      return keycloakService.getRoles(agvId).contains(Roles.AGV)
          && keycloakService.getOrganizationIds(agvId).contains(UserSession.organizationId());
    } catch (Exception e) {
      return false;
    }
  }
}
