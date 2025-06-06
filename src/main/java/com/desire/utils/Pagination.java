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

package com.desire.utils;

import java.beans.Transient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@Getter
@Setter
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
public class Pagination {

  public static final Pagination ONE_LATEST = new Pagination(1, 0, Direction.DESC);

  public static final Pagination ONE_OLDEST = new Pagination(1, 0, Direction.ASC);

  @Positive
  protected Integer pageSize = 20;

  @PositiveOrZero
  protected Integer pageNumber = 0;

  protected Direction sortDirection = Direction.DESC;

  /**
   * 
   * @param fieldNameToSortBy Name of the field to sort by. Sorting is mandatory
   * @return
   */
  @Transient
  public Pageable getPage(@NotEmpty String fieldNameToSortBy) {
    return PageRequest.of(pageNumber, pageSize, sortDirection, fieldNameToSortBy);
  }

}
