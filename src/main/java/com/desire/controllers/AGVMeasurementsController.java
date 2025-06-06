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

import java.util.List;
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
import com.desire.dtos.PostAGVMeasurementRequest;
import com.desire.dtos.SearchAGVMeasurementRequest;
import com.desire.model.entities.AGVMeasurements;
import com.desire.services.AGVMeasurementService;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = Endpoints.AGVMeasurements.TAG)
@RestController
@RequestMapping(Endpoints.AGVMeasurements.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVMeasurementsController {

  private final @NonNull AGVMeasurementService agvMeasurementService;

  @RolesAllowed({Roles.COLLECTOR, Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<AGVMeasurements> get(@Valid SearchAGVMeasurementRequest request) {
    return agvMeasurementService.search(request);
  }

  @Deprecated(forRemoval = false)
  @Schema(deprecated = true, description = "TEST feature to simulate TUI pulling data")
  @RolesAllowed({Roles.ADMIN, Roles.TECHNICAL_USER})
  @PostMapping
  public List<AGVMeasurements> post(@RequestBody @Valid PostAGVMeasurementRequest request)
      throws ValidationException {
    return agvMeasurementService.post(request);
  }
}
