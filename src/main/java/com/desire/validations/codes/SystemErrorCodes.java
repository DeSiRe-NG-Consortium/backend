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

package com.desire.validations.codes;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum SystemErrorCodes {

  // @formatter:off
  /**
   * User is not allowed to perform the given requested action.
   */
  FORBIDDEN,
  /**
   * Invalid configuration option for the given request.
   */
  INVALID_CONFIGURATION,
  /**
   * Requested action is invalid for the current state of the given object.
   */
  INVALID_OPERATION,
  /**
   * Resource is (temporarily) not available.
   */
  RESOURCE_NOT_AVAILABLE,
  /**
   * Resource could not be retrieved or does not exist (DB, Keycloak, …).
   */
  RESOURCE_NOT_FOUND,
  /**
   * Generic validation error to be used as a fallback.
   */
  VALIDATION_ERROR,
  /**
   * User is not authorized to perform the given request.
   */
  UNAUTHORIZED,
  /**
   * Generic error.
   */
  UNEXPECTED_ERROR
  // @formatter:on

}
