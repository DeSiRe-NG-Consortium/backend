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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PostOrganizationRequest;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Organizations.Fields;
import com.desire.model.repositories.OrganizationRepository;
import com.desire.services.OrganizationService;
import com.desire.utils.Constants;
import com.desire.utils.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = Endpoints.Organizations.TAG)
@RestController
@RequestMapping(Endpoints.Organizations.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class OrganizationsController {

  private final @NonNull OrganizationRepository organizationDao;
  private final @NonNull OrganizationService organizationService;

  @RolesAllowed({Roles.ADMIN})
  @GetMapping
  public Page<Organizations> get(Pagination pagination) {
    return organizationDao.findAll(pagination.getPage(Fields.name));
  }

  @RolesAllowed({Roles.ADMIN})
  @PostMapping
  public Organizations post(@RequestBody @Valid PostOrganizationRequest request) {
    return organizationService.post(request);
  }

  // ###########
  // ## /{id} ##
  // ###########

  @Operation(responses = {@ApiResponse(responseCode = "501",
      description = Constants.ControllerDocumentation.MSG_FEATURE_NOT_IMPLEMENTED)})
  @RolesAllowed({Roles.ADMIN})
  @GetMapping(Endpoints.Organizations.ID)
  public ResponseEntity<Void> getById(@PathVariable String organizationId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Operation(responses = {@ApiResponse(responseCode = "501",
      description = Constants.ControllerDocumentation.MSG_FEATURE_NOT_IMPLEMENTED)})
  @RolesAllowed({Roles.ADMIN})
  @PatchMapping(Endpoints.Organizations.ID)
  public ResponseEntity<Void> patchById(@PathVariable String organizationId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Operation(responses = {@ApiResponse(responseCode = "501",
      description = Constants.ControllerDocumentation.MSG_FEATURE_NOT_IMPLEMENTED)})
  @RolesAllowed({Roles.ADMIN})
  @DeleteMapping(Endpoints.Organizations.ID)
  public ResponseEntity<Void> deleteById(@PathVariable String organizationId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

}
