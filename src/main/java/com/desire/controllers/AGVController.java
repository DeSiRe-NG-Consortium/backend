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
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.AGV;
import com.desire.constants.Roles;
import com.desire.dtos.PostAgvStatusEventRequest;
import com.desire.dtos.SearchAGVCommandRequest;
import com.desire.events.AGVCommandStreamService;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVStatusEvents;
import com.desire.services.AGVCommandService;
import com.desire.services.AGVStatusEventService;
import com.desire.services.AGVStatusEventValidationService;
import com.desire.utils.Pagination;
import com.desire.utils.UserSession;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = AGV.TAG, description = "Operations for the current AGV user, only usable by AGV users.")
@RestController
@RequestMapping(Endpoints.AGV.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVController {

  private final @NonNull AGVCommandService agvCommandService;
  private final @NonNull AGVCommandStreamService agvCommandStream;
  private final @NonNull AGVStatusEventService agvStatusEventService;
  private final @NonNull AGVStatusEventValidationService agvstatusEventValidationService;

  // ##########################
  // ## PATH: /status-events ##
  // ##########################

  @RolesAllowed({Roles.AGV})
  @GetMapping(AGV.STATUS_EVENTS)
  public Page<AGVStatusEvents> getStatusEvents(@Valid Pagination pagination) {
    return agvStatusEventService.search(UserSession.id(), pagination);
  }

  @RolesAllowed(Roles.AGV)
  @PostMapping(AGV.STATUS_EVENTS)
  public AGVStatusEvents postStatusEvent(@RequestBody @Valid PostAgvStatusEventRequest request)
      throws ValidationException {
    agvstatusEventValidationService.validatePost(request);

    return agvStatusEventService.post(UserSession.id(), request);
  }

  // #####################
  // ## PATH: /commands ##
  // #####################

  @RolesAllowed({Roles.AGV})
  @GetMapping(AGV.COMMANDS)
  public Page<AGVCommands> getCommands(@Valid SearchAGVCommandRequest request) {
    return agvCommandService.search(UserSession.id(), request);
  }

  // ############################
  // ## PATH: /commands/stream ##
  // ############################

  @RolesAllowed(Roles.AGV)
  @GetMapping(path = AGV.COMMANDS_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseBodyEmitter getCommandStream() {
    return agvCommandStream.subscribe(UserSession.id());
  }
}
