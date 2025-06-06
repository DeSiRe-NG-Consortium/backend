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

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Security roles coming from Keycloak integration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Roles {

  public static final String ANALYST = "ANALYST"; // read only user
  public static final String COLLECTOR = "COLLECTOR";
  public static final String AGV = "AGV";
  public static final String OPERATOR = "OPERATOR";
  public static final String MANAGER = "MANAGER";
  public static final String ADMIN = "ADMIN";

  public static final String TECHNICAL_USER = "TECHNICAL_USER";
  public static final String TECHNICAL_ADMIN = "TECHNICAL_ADMIN";

  // List of all functional user roles
  public static final List<String> LIST =
      List.of(ANALYST, COLLECTOR, AGV, OPERATOR, MANAGER, ADMIN);

}
