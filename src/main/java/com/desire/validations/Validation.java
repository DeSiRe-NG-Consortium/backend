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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Validation implements Serializable {

  @Serial
  private static final long serialVersionUID = -3689901484628540064L;

  @Schema(description = "Error codes returned by validation process")
  private List<ValidationEntry> errors = new ArrayList<>();

  public Validation(List<ValidationEntry> errors) {
    this.errors = errors;
  }

  @SuppressWarnings("rawtypes")
  @Schema(description = "Operation-specific error codes")
  public Enum[] getOperationErrorCodes() {
    return new Enum[] {};
  }

  @Schema(description = "System-wide error codes")
  public ValidationErrorCodes[] getSystemErrorCodes() {
    return ValidationErrorCodes.values();
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class ValidationEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = -8911358391021175390L;

    private Enum<?> code;
    private String message;

    @Override
    public String toString() {
      return code + ": " + message;
    }
  }

  public void addError(Enum<?> code) {
    this.errors.add(new ValidationEntry(code, null));
  }

  public void addError(Enum<?> code, String message) {
    this.errors.add(new ValidationEntry(code, message));
  }

  public void addError(String message) {
    this.errors.add(new ValidationEntry(ValidationErrorCodes.VALIDATION_ERROR, message));
  }

  /**
   * @return True if validation object contains validation errors
   */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /**
   * <p>
   * Runs all constrains validations programmatically and throws an exception if constrains
   * violations were found.
   * </p>
   *
   * <p>
   * Used to centralize parameters validations over all the system.
   * </p>
   *
   * @param bean Validation object
   * @throws ConstraintViolationException Thrown if validation fails.
   */
  public static void validate(Object bean) throws ConstraintViolationException {
    Validator validator;
    try (ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }

    Set<ConstraintViolation<Object>> violations = validator.validate(bean);

    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

}
