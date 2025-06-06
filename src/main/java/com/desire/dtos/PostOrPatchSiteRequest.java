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

import com.desire.model.entities.Coordinates;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.Sites;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PostOrPatchSiteRequest {

  @NotEmpty
  private String name;

  private String description;

  @NotNull
  private Boolean available;

  @Positive
  private Double coordinateScaleFactor;

  private Coordinates coordinateOrigin;

  private Coordinates minCoordinates;

  private Coordinates maxCoordinates;

  @Positive
  private Float mapTileResolution;

  private String floorPlanImagePath;

  public Sites toEntity(Organizations organization) {
    Sites site = new Sites();
    updateEntity(site);
    site.setOrganization(organization);

    return site;
  }

  public Sites updateEntity(Sites site) {
    site.setName(name);
    site.setDescription(description);
    site.setAvailable(available);
    site.setCoordinateScaleFactor(coordinateScaleFactor);
    site.setCoordinateOrigin(coordinateOrigin);
    site.setMinCoordinates(minCoordinates);
    site.setMaxCoordinates(maxCoordinates);
    site.setMapTileResolution(mapTileResolution);
    site.setFloorPlanImagePath(floorPlanImagePath);

    return site;
  }
}
