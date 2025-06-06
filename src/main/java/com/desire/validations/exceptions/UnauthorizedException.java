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

package com.desire.validations.exceptions;

import java.io.Serial;
import java.util.List;
import com.desire.validations.Validation;
import com.desire.validations.Validation.ValidationEntry;
import com.desire.validations.codes.SystemErrorCodes;

/**
 * Exception used to indicate that a requesting user is not authorized to access or modify a
 * resource.
 */
public class UnauthorizedException extends ValidationException {

  @Serial
  private static final long serialVersionUID = -7865016625959845394L;

  public UnauthorizedException() {
    super(new Validation(
        List.of(new ValidationEntry(SystemErrorCodes.UNAUTHORIZED, "Unauthorized."))));
  }
}
