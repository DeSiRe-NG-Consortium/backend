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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.desire.constants.Constraints.Text;
import com.desire.constants.Endpoints.AGVs;
import com.desire.constants.Roles;
import com.desire.dtos.AGVDto;
import com.desire.dtos.AGVUpdateDto;
import com.desire.dtos.SearchAGVRequest;
import com.desire.events.AGVStreamService;
import com.desire.services.AGVService;
import com.desire.validations.annotations.AGVId;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = AGVs.TAG)
@RestController
@RequestMapping(AGVs.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVsController {

  private final @NonNull AGVService agvService;
  private final @NonNull AGVStreamService agvStreamService;

  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<AGVDto> get(@Valid SearchAGVRequest request) {
    return agvService.search(request);
  }

  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping(AGVs.ID)
  public AGVDto getById(@PathVariable @AGVId @Pattern(regexp = Text.Pattern.UUID) String agvId) {
    return agvService.search(SearchAGVRequest.builder().id(agvId).build()).get().findFirst()
        .orElse(null);
  }

  @ApiResponse(content = @Content(schema = @Schema(implementation = AGVUpdateDto.class)))
  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping(path = AGVs.STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseBodyEmitter getStreamById(
      @PathVariable @AGVId @Pattern(regexp = Text.Pattern.UUID) String agvId) {
    return agvStreamService.subscribe(agvId);
  }

}
