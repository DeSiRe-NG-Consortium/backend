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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Constraints.Text;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.Organizations;
import com.desire.constants.Roles;
import com.desire.dtos.PostOrPatchSiteRequest;
import com.desire.dtos.SearchSiteRequest;
import com.desire.model.entities.Sites;
import com.desire.services.SiteService;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = Organizations.TAG)
@RestController
@RequestMapping(Endpoints.Organizations.Sites.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class OrganizationSitesController {

  private final @NonNull SiteService siteService;

  @RolesAllowed({Roles.ANALYST, Roles.COLLECTOR, Roles.OPERATOR, Roles.MANAGER, Roles.ADMIN})
  @GetMapping
  public Page<Sites> get(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String organizationId,
      @Valid SearchSiteRequest request) {
    return siteService.search(organizationId, request);
  }

  @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
  @PostMapping
  public Sites post(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String organizationId,
      @RequestBody @Valid PostOrPatchSiteRequest request) throws ValidationException {
    return siteService.post(organizationId, request);
  }

  // ###########
  // ## /{id} ##
  // ###########

  @RolesAllowed({Roles.ANALYST, Roles.COLLECTOR, Roles.OPERATOR, Roles.MANAGER, Roles.ADMIN})
  @GetMapping(Organizations.Sites.ID)
  public Sites getById(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String organizationId,
      @PathVariable @Pattern(regexp = Text.Pattern.UUID) String siteId) {
    Page<Sites> sites =
        siteService.search(organizationId, SearchSiteRequest.builder().id(siteId).build());

    return sites.get().findFirst().orElse(null);
  }

  @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
  @PatchMapping(Endpoints.Organizations.Sites.ID)
  public Sites patchById(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String organizationId,
      @PathVariable @Pattern(regexp = Text.Pattern.UUID) String siteId,
      @RequestBody @Valid PostOrPatchSiteRequest request) throws ValidationException {
    return siteService.patch(organizationId, siteId, request);
  }

  @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
  @DeleteMapping(Endpoints.Organizations.Sites.ID)
  public void deleteById(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String organizationId,
      @PathVariable @Pattern(regexp = Text.Pattern.UUID) String siteId) throws ValidationException {
    siteService.delete(organizationId, siteId);
  }

}
