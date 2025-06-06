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

package com.desire.services;

import com.desire.dtos.SearchInterpolatedMeasurementsRequest;
import com.desire.model.entities.Campaigns;
import com.desire.validations.Validation;
import com.desire.validations.codes.SystemErrorCodes;
import com.desire.validations.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class InterpolatedMeasurementsValidationService {

  public void validateSearch(Campaigns campaign,
      SearchInterpolatedMeasurementsRequest searchRequest) throws ValidationException {
    Validation validation = new Validation();

    if (searchRequest.isMinCoordinatesPresent()
        && campaign.getSite().getMinCoordinates() != null && (
        searchRequest.getMinX() < campaign.getSite().getMinCoordinates().getX()
            || searchRequest.getMinY() < campaign.getSite().getMinCoordinates().getY())) {
      validation.addError(SystemErrorCodes.VALIDATION_ERROR, "Min coordinates out of range");
    }

    if (searchRequest.isMaxCoordinatesPresent()
        && campaign.getSite().getMaxCoordinates() != null && (
        searchRequest.getMaxX() > campaign.getSite().getMaxCoordinates().getX()
            || searchRequest.getMaxY() > campaign.getSite().getMaxCoordinates().getY())) {
      validation.addError(SystemErrorCodes.VALIDATION_ERROR, "Max coordinates out of range");
    }

    if (searchRequest.isMinCoordinatesPresent()
        && searchRequest.isMaxCoordinatesPresent() && (
        searchRequest.getMinX() > searchRequest.getMaxX()
            || searchRequest.getMinY() > searchRequest.getMaxY())) {
      validation.addError(SystemErrorCodes.VALIDATION_ERROR, "Invalid coordinates");
    }

    if (!searchRequest.isMinCoordinatesPresent()
        && campaign.getSite().getMinCoordinates() == null) {
      validation.addError(SystemErrorCodes.VALIDATION_ERROR, "No min coordinates present");
    }

    if (!searchRequest.isMaxCoordinatesPresent()
        && campaign.getSite().getMaxCoordinates() == null) {
      validation.addError(SystemErrorCodes.VALIDATION_ERROR, "No max coordinates present");
    }

    if (validation.hasErrors()) {
      throw new ValidationException(validation);
    }
  }
}
