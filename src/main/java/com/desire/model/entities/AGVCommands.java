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
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryInit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@Document(collection = "agv_commands")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class AGVCommands {

  public static final Set<AGVCommandState> PENDING_STATES =
      Set.of(AGVCommandState.CREATED, AGVCommandState.SENT);

  public static final Set<AGVCommandState> OPEN_STATES =
      Set.of(AGVCommandState.ACKNOWLEDGED, AGVCommandState.SENT);

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  @NotEmpty
  private String agvId;

  @NotNull
  @QueryInit("site.organization")
  private Campaigns campaign;

  @NotNull
  private AGVCommandType type;

  @NotNull
  private AGVCommandState state = AGVCommandState.CREATED;

  /**
   * When event was created in client.
   */
  @NotNull
  private LocalDateTime measureMoment;

  /**
   * When event is created in backend.
   */
  @NotNull
  private LocalDateTime createMoment = LocalDateTime.now();

  /**
   * When event was last sent to AGV.
   */
  private LocalDateTime latestSendMoment;

  private LocalDateTime updateMoment;

  @Valid
  private AVGCommandData data;

  @Data
  public static class AVGCommandData {
    @Valid
    private Coordinates coordinates;

    private String message;
  }

  @JsonIgnore
  public boolean isCreated() {
    return AGVCommandState.CREATED.equals(state);
  }

  @JsonIgnore
  public boolean isPending() {
    return AGVCommandState.CREATED.equals(state) || AGVCommandState.SENT.equals(state);
  }

  @JsonIgnore
  public boolean isClosed() {
    return AGVCommandState.ABORTED.equals(state) || AGVCommandState.COMPLETED.equals(state)
        || AGVCommandState.OBSOLETE.equals(state) || AGVCommandState.TIMEOUT.equals(state);
  }

  public void setStateFromStatusEvent(AGVStatusEvents statusEvent) {
    if (statusEvent == null || statusEvent.getEventType() == null) {
      return;
    }

    switch (statusEvent.getEventType()) {
      case ACKNOWLEDGE_REQUEST:
        setState(AGVCommandState.ACKNOWLEDGED);
        break;
      case COMPLETE_REQUEST:
        setState(AGVCommandState.COMPLETED);
        break;
      case REJECT_REQUEST:
        setState(AGVCommandState.REJECTED);
        break;
      default:
    }
  }
}
