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
import com.desire.model.entities.AGVStatusEvents;
import com.desire.types.AGVStatusEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PostAgvStatusEventRequest {

  @NotNull
  @Schema(implementation = AGVStatusEventType.class)
  private AGVStatusEventType eventType;

  /**
   * When event was created in client
   */
  @NotNull
  private LocalDateTime measureMoment;

  @Pattern(regexp = Text.Pattern.UUID)
  private String commandId;

  private String data;

  public AGVStatusEvents toEntity(String agvId, AGVCommands command) {
    AGVStatusEvents statusEvent = new AGVStatusEvents();

    statusEvent.setEventType(eventType);
    statusEvent.setAgvId(agvId);
    statusEvent.setCommand(command);
    statusEvent.setData(data);
    statusEvent.setMeasureMoment(measureMoment);

    return statusEvent;
  }

  public boolean relatesToCommand() {
    return AGVStatusEventType.ACKNOWLEDGE_REQUEST.equals(eventType)
        || AGVStatusEventType.COMPLETE_REQUEST.equals(eventType)
        || AGVStatusEventType.REJECT_REQUEST.equals(eventType);
  }
}
