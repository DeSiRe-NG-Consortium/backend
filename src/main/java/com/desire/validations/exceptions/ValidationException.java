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
import com.desire.validations.Validation;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Base exception for handling application validations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ValidationException extends Exception {

  @Serial
  private static final long serialVersionUID = -8641650708071580526L;

  private final Validation validation;

}
