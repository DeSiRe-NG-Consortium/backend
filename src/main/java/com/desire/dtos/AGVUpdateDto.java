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

import com.desire.model.entities.AGVMeasurements;
import java.time.LocalDateTime;
import com.desire.model.entities.AGVPositions;
import com.desire.model.entities.AGVStatusEvents;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class AGVUpdateDto {

  public AGVUpdateDto(List<AGVMeasurements> measurements) {
    this.measurements = measurements;
  }

  public AGVUpdateDto(AGVPositions position) {
    this.position = position;
  }

  public AGVUpdateDto(AGVStatusEvents statusEvent) {
    this.status = statusEvent;
  }

  private LocalDateTime updateMoment = LocalDateTime.now();

  private AGVStatusEvents status;

  private AGVPositions position;

  private List<AGVMeasurements> measurements;
}
