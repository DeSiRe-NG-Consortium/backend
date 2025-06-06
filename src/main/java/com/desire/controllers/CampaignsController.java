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

import com.desire.dtos.GetInterpolatedMeasurementResponse;
import com.desire.dtos.SearchInterpolatedMeasurementsRequest;
import com.desire.services.InterpolatedMeasurementsService;
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
import com.desire.constants.Endpoints;
import com.desire.constants.Roles;
import com.desire.dtos.PatchCampaignRequest;
import com.desire.dtos.PostCampaignRequest;
import com.desire.dtos.SearchCampaignRequest;
import com.desire.dtos.SearchCampaignRequest.SearchCampaignRequestBuilder;
import com.desire.model.entities.Campaigns;
import com.desire.services.CampaignService;
import com.desire.services.CampaignValidationService;
import com.desire.validations.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Validated
@Tag(name = Endpoints.Campaigns.TAG)
@RestController
@RequestMapping(Endpoints.Campaigns.PATH)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CampaignsController {

  private final @NonNull CampaignService campaignService;
  private final @NonNull CampaignValidationService campaignValidationService;
  private final @NonNull InterpolatedMeasurementsService interpolatedMeasurementsService;

  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping
  public Page<Campaigns> get(@Valid SearchCampaignRequest request) {
    return campaignService.search(request);
  }

  @RolesAllowed({Roles.OPERATOR, Roles.MANAGER})
  @PostMapping
  public Campaigns post(@RequestBody @Valid PostCampaignRequest request)
      throws ValidationException {
    campaignValidationService.validatePost(request);

    return campaignService.post(request);
  }

  // ##################
  // ## PATH: /{id}/ ##
  // ##################

  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping(Endpoints.Campaigns.ID)
  // FIXME: Fix for non-compliant UUIDs of TH-OWL paper campaigns.
  // public Campaigns getById(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String campaignId) {
  public Campaigns getById(@PathVariable String campaignId) {
    SearchCampaignRequestBuilder builder = SearchCampaignRequest.builder().id(campaignId);

    Page<Campaigns> campaigns = campaignService.search(builder.build());

    return campaigns.isEmpty() ? null : campaigns.getContent().get(0);
  }

  @RolesAllowed({Roles.OPERATOR, Roles.MANAGER})
  @PatchMapping(Endpoints.Campaigns.ID)
  public Campaigns patchById(@PathVariable @Pattern(regexp = Text.Pattern.UUID) String campaignId,
      @RequestBody @Valid PatchCampaignRequest request) throws ValidationException {
    campaignValidationService.validatePatch(campaignId, request);

    return campaignService.patch(campaignId, request);
  }

  @RolesAllowed({Roles.ANALYST, Roles.OPERATOR, Roles.MANAGER})
  @GetMapping(Endpoints.Campaigns.INTERPOLATED_MEASUREMENTS)
  public GetInterpolatedMeasurementResponse getInterpolatedMeasurementsById(
      @PathVariable String campaignId, @Valid SearchInterpolatedMeasurementsRequest searchRequest)
      throws ValidationException {
    return interpolatedMeasurementsService.queryInterpolatedMeasurements(campaignId, searchRequest);
  }
}
