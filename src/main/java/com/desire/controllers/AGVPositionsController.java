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

package com.desire.controllers;

import com.desire.services.FakePositionService;
import com.desire.utils.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PostAGVPositionRequest;
import com.desire.dtos.SearchAGVPositionRequest;
import com.desire.model.entities.AGVPositions;
import com.desire.services.AGVPositionService;
import com.desire.services.AGVPositionValidationService;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = Endpoints.AGVPositions.TAG)
@RestController
@RequestMapping(Endpoints.AGVPositions.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVPositionsController {

  private final @NonNull AGVPositionValidationService positionValidationService;
  private final @NonNull AGVPositionService positionService;
  private final @NonNull FakePositionService fakePositionService;

  @RolesAllowed({Roles.AGV, Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<AGVPositions> get(@Valid SearchAGVPositionRequest request) {
    return positionService.search(request);
  }

  @RolesAllowed(Roles.AGV)
  @PostMapping
  public AGVPositions post(@RequestBody @Valid PostAGVPositionRequest request)
      throws ValidationException {
    positionValidationService.validatePost(request);

    fakePositionService.generateFakeMeasurements(request, UserSession.id());

    return positionService.post(request);
  }

}
