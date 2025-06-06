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

package com.desire.model.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.desire.constants.Constraints;
import com.desire.constants.Constraints.Text;
import com.desire.types.CampaignState;
import com.desire.validations.annotations.AGVId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@Document(collection = "campaigns")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class Campaigns {

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  @NotEmpty
  private String name;

  @NotNull
  private CampaignState state;

  @NotNull
  private Sites site;

  private List<Configuration> configurations = new ArrayList<>();

  private LocalDateTime startMoment;

  private LocalDateTime stopMoment;

  @NotNull
  private LocalDateTime createMoment = LocalDateTime.now();

  @NotNull
  private LocalDateTime updateMoment = LocalDateTime.now();

  @JsonIgnore
  public boolean isState(CampaignState state) {
    return state.equals(this.state);
  }

  @JsonIgnore
  public boolean isCreated() {
    return isState(CampaignState.CREATED);
  }

  @JsonIgnore
  public boolean isRunning() {
    return isState(CampaignState.RUNNING);
  }

  @JsonIgnore
  public boolean isActive() {
    return isCreated() || isRunning();
  }

  @JsonIgnore
  public boolean isCompleted() {
    return isState(CampaignState.COMPLETED);
  }

  @JsonIgnore
  public boolean isAborted() {
    return isState(CampaignState.ABORTED);
  }

  @JsonIgnore
  public boolean isClosed() {
    return isAborted() || isCompleted();
  }

  @Data
  public static class Configuration {

    @NotNull
    @Size(max = Constraints.Text.Size.SMALL)
    @Pattern(regexp = Constraints.Text.Pattern.TECHNICAL_NAME_PATTERN)
    private String clientId;

    @NotNull
    @Size(max = Constraints.Text.Size.SMALL)
    @Pattern(regexp = Constraints.Text.Pattern.TECHNICAL_NAME_PATTERN)
    private String endpointId;

    @NotNull
    @Size(max = Constraints.Text.Size.SMALL)
    @Pattern(regexp = Constraints.Text.Pattern.TECHNICAL_NAME_PATTERN)
    private String orchestratorId;

    @AGVId
    @Pattern(regexp = Text.Pattern.UUID)
    private String agvId;
  }
}
