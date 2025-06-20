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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Coordinates {

  /**
   * When position was saved in AGV machine
   */
  @NotNull
  private LocalDateTime measureMoment;

  @NotNull
  private Float x;

  @NotNull
  private Float y;

  @NotNull
  private Float z;

  private Float rotationX;

  private Float rotationY;

  private Float rotationZ;

  public Coordinates(Float x, Float y, Float z) {
    this.measureMoment = LocalDateTime.now();
    this.x = x;
    this.y = y;
    this.z = z;
  }
}
