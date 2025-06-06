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

import java.util.ArrayList;
import java.util.List;
import com.desire.constants.Constraints.Text;
import com.desire.model.entities.Coordinates;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PostAGVPositionRequest {

  @NotEmpty
  @Pattern(regexp = Text.Pattern.UUID)
  private String campaignId;

  @NotEmpty
  private List<Coordinates> coordinates = new ArrayList<>();

}
