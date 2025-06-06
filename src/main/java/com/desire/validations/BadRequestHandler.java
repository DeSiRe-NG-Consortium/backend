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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import com.desire.validations.exceptions.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Handles validation errors exceptions to offer a more friendly message list
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class BadRequestHandler {

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({HandlerMethodValidationException.class})
  public Validation methodMissingServletRequestParameterException(
      HandlerMethodValidationException ex) {
    Validation validation = new Validation();

    validation.addError(String.valueOf(ex.getDetailMessageArguments()[0]));

    return validation;
  }

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({MissingServletRequestParameterException.class})
  public Validation methodMissingServletRequestParameterException(
      MissingServletRequestParameterException ex) {
    Validation validation = new Validation();

    validation.addError(ValidationErrorCodes.VALIDATION_ERROR,
        parseErrorMessage(ex.getParameterName(), ex.getMessage()));

    return validation;
  }

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({BindException.class})
  public Validation methodBindException(BindException ex) {
    BindingResult result = ex.getBindingResult();
    List<FieldError> fieldErrors = result.getFieldErrors();
    return processFieldErrors(fieldErrors);
  }

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({MethodArgumentNotValidException.class})
  public Validation methodArgumentNotValidException(MethodArgumentNotValidException ex) {
    BindingResult result = ex.getBindingResult();
    List<FieldError> fieldErrors = result.getFieldErrors();
    return processFieldErrors(fieldErrors);
  }

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({ConstraintViolationException.class})
  public Validation toResponse(ConstraintViolationException ex) {
    Validation validation = new Validation();

    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      validation.addError(ValidationErrorCodes.VALIDATION_ERROR,
          parseErrorMessage(violation.getPropertyPath().toString(), violation.getMessage()));
    }

    return validation;
  }

  @ResponseStatus(BAD_REQUEST)
  @ResponseBody
  @ExceptionHandler({ValidationException.class})
  public Validation handleValidationException(ValidationException ex) {
    return ex.getValidation();
  }

  private Validation processFieldErrors(List<FieldError> fieldErrors) {
    Validation validation = new Validation();

    for (FieldError fieldError : fieldErrors) {
      validation.addError(ValidationErrorCodes.VALIDATION_ERROR,
          parseErrorMessage(fieldError.getField(), fieldError.getDefaultMessage()));
    }

    return validation;
  }

  /**
   * Concatenates the field and the message
   *
   * @param field
   * @param message
   * @return
   */
  private String parseErrorMessage(String field, String message) {
    return field + " – " + message;
  }

}
