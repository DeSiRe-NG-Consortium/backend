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
import com.desire.constants.Constraints.Text;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVCommands.AVGCommandData;
import com.desire.model.entities.Campaigns;
import com.desire.types.AGVCommandType;
import com.desire.validations.annotations.AGVCommandTypeSubset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PostAgvCommandRequest {

  @NotNull
  @Pattern(regexp = Text.Pattern.UUID)
  private String campaignId;

  @NotNull
  @Schema(implementation = AGVCommandType.class)
  @AGVCommandTypeSubset(anyOf = {AGVCommandType.GO_TO})
  private AGVCommandType eventType;

  /**
   * When event was created in client
   */
  @NotNull
  private LocalDateTime measureMoment;

  private AVGCommandData data;

  public AGVCommands toEntity(String agvId, Campaigns campaign) {
    AGVCommands command = new AGVCommands();

    command.setType(eventType);
    command.setAgvId(agvId);
    command.setCampaign(campaign);
    command.setData(data);
    command.setMeasureMoment(measureMoment);

    return command;
  }

}
