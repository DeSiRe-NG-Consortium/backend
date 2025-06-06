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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * This collection saves all TUI messages that failed and needs to be reprocessed through a
 * scheduled task to synchronize data in between our system and TUI's
 */
@Data
@FieldNameConstants
@Document(collection = "tui_agv_position_messages")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class TuiAGVPositionMessages {

  public TuiAGVPositionMessages(AGVPositions position) {
    this.position = position;
  }

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  @NotNull
  private AGVPositions position;

  /**
   * Last message attempt that failed
   */
  @NotNull
  private LocalDateTime latestAttemptMoment;

  @NotNull
  private LocalDateTime createMoment = LocalDateTime.now();

  private List<String> errors = new ArrayList<>();

}
