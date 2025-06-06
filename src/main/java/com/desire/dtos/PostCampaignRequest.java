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

package com.desire.dtos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.desire.constants.Constraints.Text;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Campaigns.Configuration;
import com.desire.model.entities.Sites;
import com.desire.types.CampaignState;
import com.desire.validations.annotations.CampaignStateSubset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PostCampaignRequest {

  @NotEmpty
  private String name;

  @NotNull
  @Schema(implementation = CampaignState.class)
  @CampaignStateSubset(anyOf = {CampaignState.CREATED, CampaignState.RUNNING})
  private CampaignState state = CampaignState.CREATED;

  @NotEmpty
  @Pattern(regexp = Text.Pattern.UUID)
  private String siteId;

  @NotEmpty
  @Valid
  private List<Configuration> configurations = new ArrayList<>();

  private Boolean generateFakeMeasurements = false;

  private Boolean generateFakePositions = false;

  public Campaigns toEntity(Sites site) {
    Campaigns campaign = new Campaigns();
    campaign.setSite(site);
    campaign.setName(name);
    campaign.setState(state);
    campaign.setConfigurations(configurations);

    if (CampaignState.RUNNING.equals(campaign.getState())) {
      campaign.setStartMoment(LocalDateTime.now());
    }

    return campaign;
  }
}
