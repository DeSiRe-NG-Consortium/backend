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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGVs;
import com.desire.constants.Roles;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.services.AGVStatusEventService;
import com.desire.services.AGVStatusEventValidationService;
import com.desire.utils.Pagination;
import com.desire.validations.annotations.AGVId;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = AGVs.TAG)
@RestController
@RequestMapping(Endpoints.AGVs.StatusEvents.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVsStatusEventController {

  private final @NonNull AGVStatusEventService agvStatusEventService;
  private final @NonNull AGVStatusEventValidationService agvstatusEventValidationService;

  @RolesAllowed({Roles.AGV, Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<AGVStatusEvents> get(@PathVariable @AGVId String agvId,
      @Valid Pagination pagination) {
    return agvStatusEventService.search(agvId, pagination);
  }

  /**
   * @deprecated Replaced by {@link AGVController#postStatusEvent(PostAgvStatusEventRequest)}.
   */
  @Deprecated(forRemoval = false)
  @Schema(deprecated = true, description = "Replaced by /agv/status-events.")
  @RolesAllowed(Roles.AGV)
  @PostMapping
  public AGVStatusEvents post(@PathVariable @AGVId String agvId,
      @RequestBody @Valid PostAgvStatusEventRequest request) throws ValidationException {
    agvstatusEventValidationService.validatePost(request);

    return agvStatusEventService.post(agvId, request);
  }

}
