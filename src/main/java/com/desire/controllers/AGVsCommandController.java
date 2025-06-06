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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Constraints.Text;
import com.desire.constants.Endpoints.AGVs;
import com.desire.constants.Endpoints.AGVs.Commands;
import com.desire.constants.Roles;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.dtos.SearchAGVCommandRequest;
import com.desire.model.entities.AGVCommands;
import com.desire.services.AGVCommandService;
import com.desire.types.AGVCommandState;
import com.desire.validations.annotations.AGVCommandStateSubset;
import com.desire.validations.annotations.AGVId;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = AGVs.TAG)
@RestController
@RequestMapping(Commands.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVsCommandController {

  private final @NonNull AGVCommandService agvCommandService;

  @RolesAllowed({Roles.AGV, Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<AGVCommands> get(@PathVariable @AGVId String agvId,
      @Valid SearchAGVCommandRequest request) {
    return agvCommandService.search(agvId, request);
  }

  @RolesAllowed({Roles.OPERATOR})
  @PostMapping
  public AGVCommands post(@PathVariable @AGVId String agvId,
      @RequestBody @Valid PostAgvCommandRequest request) throws ValidationException {
    return agvCommandService.post(agvId, request);
  }

  // #################
  // ## PATH: /{id} ##
  // #################

  @RolesAllowed({Roles.AGV, Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping(path = Commands.ID)
  public AGVCommands getById(@PathVariable @AGVId String agvId,
      @PathVariable @Pattern(regexp = Text.Pattern.UUID) String commandId) {
    SearchAGVCommandRequest request =
        SearchAGVCommandRequest.builder().commandId(commandId).build();

    return agvCommandService.search(agvId, request).stream().findAny().orElse(null);
  }

  @RolesAllowed({Roles.OPERATOR})
  @PatchMapping(path = Commands.ID)
  public AGVCommands patchById(@PathVariable @AGVId String agvId,
      @PathVariable @Pattern(regexp = Text.Pattern.UUID) String commandId,
      @RequestBody @AGVCommandStateSubset(anyOf = {AGVCommandState.ABORTED}) AGVCommandState state)
      throws ValidationException {
    return agvCommandService.patch(agvId, commandId, state);
  }
}
