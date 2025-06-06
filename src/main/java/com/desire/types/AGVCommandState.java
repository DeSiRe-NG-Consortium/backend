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

public enum AGVCommandState {

  /**
   * Request was registered in backend.
   */
  CREATED,
  /**
   * Request was sent from backend to AGV.
   */
  SENT,
  /**
   * Request was acknowledged by AGV.
   */
  ACKNOWLEDGED,
  /**
   * Request was completed by AGV.
   */
  COMPLETED,
  /**
   * AGV rejected to perform request.
   */
  REJECTED,
  /**
   * Request was aborted by user.
   */
  ABORTED,
  /**
   * Request will not be sent to AGV because it is obsolete (e.g., parent campaign has been
   * aborted).
   */
  OBSOLETE,
  /**
   * Timeout has been reached; request will not be sent to AGV again.
   */
  TIMEOUT
}
