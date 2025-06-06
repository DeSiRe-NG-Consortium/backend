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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@Document(collection = "sites")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class Sites {

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  @NotEmpty
  private String name;

  private String description;

  @NotNull
  private boolean available;

  @NotNull
  private Organizations organization;

  @NotNull
  private LocalDateTime createMoment = LocalDateTime.now();

  @Positive
  @Schema(description = "Factor from meters to pixels in floor plan image.")
  private Double coordinateScaleFactor = 1.0;

  @Schema(description = "Source of AGV coordinate system in floor plan image (in pixels).")
  private Coordinates coordinateOrigin;

  private Coordinates minCoordinates;

  private Coordinates maxCoordinates;

  @Positive
  @Schema(description = "Resolution of map tiles to draw, in meters.")
  private Float mapTileResolution = 1f;

  private String floorPlanImagePath;
}
