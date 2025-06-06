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
import com.desire.types.AGVStatusEventType;
import com.querydsl.core.annotations.QueryInit;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@Document(collection = "agv_status_events")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class AGVStatusEvents {

  public static final Set<AGVStatusEventType> AGV_LIFECYCLE_EVENT_TYPES =
      Set.of(AGVStatusEventType.ONLINE, AGVStatusEventType.SHUTDOWN);

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  private String agvId;

  @NotNull
  private AGVStatusEventType eventType;

  @QueryInit("campaign.site.organization")
  private AGVCommands command;

  /**
   * When event was created in client
   */
  @NotNull
  private LocalDateTime measureMoment;

  /**
   * When event is created in backend
   */
  @NotNull
  private LocalDateTime createMoment = LocalDateTime.now();

  private String data;
}
