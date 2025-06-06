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

import java.util.Arrays;
import com.desire.validations.annotations.CampaignStateSubset;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CampgaignStateSubsetValidator
    implements ConstraintValidator<CampaignStateSubset, Enum<?>> {
  private Enum<?>[] subset;

  @Override
  public void initialize(CampaignStateSubset constraint) {
    this.subset = constraint.anyOf();
  }

  @Override
  public boolean isValid(Enum<?> value, ConstraintValidatorContext context) {
    return value == null || Arrays.asList(subset).contains(value);
  }
}
