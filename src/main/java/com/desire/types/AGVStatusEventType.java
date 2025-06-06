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

package com.desire.types;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum AGVStatusEventType {

  /**
   * AGV reports to be online and available.
   */
  ONLINE,
  /**
   * AGV reports to have received and accepted request.
   */
  ACKNOWLEDGE_REQUEST,
  /**
   * AGV reports to have completed request.
   */
  COMPLETE_REQUEST,
  /**
   * AGV rejects to perform request.
   */
  REJECT_REQUEST,
  /**
   * AGV reports system error.
   */
  ERROR,
  /**
   * AGV is going offline.
   */
  SHUTDOWN;

}
