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

package com.desire.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraints {

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Text {

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Pattern {
      public static final String TECHNICAL_NAME_PATTERN = "^[0-9a-zA-Z_\\-\\.]+$";

      public static final String UUID =
          "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Size {
      public static final int SMALL = 255;

      public static final int UUID = 36;
    }

  }

}
