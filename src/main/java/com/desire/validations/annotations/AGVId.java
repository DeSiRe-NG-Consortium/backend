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

package com.desire.validations.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.desire.validations.AGVIdValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Check if the AGV ID is authorized for the requesting user
 */
@Documented
@Constraint(validatedBy = AGVIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AGVId {

  String message() default "User is not authorized to manage the given AGV";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

}
