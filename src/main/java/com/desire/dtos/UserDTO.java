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

import java.util.List;
import com.desire.utils.UserSession;
import lombok.Data;

@Data
public class UserDTO {

  public UserDTO() {
    this.id = UserSession.id();
    this.firstName = UserSession.name();
    this.lastName = UserSession.lastName();
    this.fullName = UserSession.fullName();
    this.email = UserSession.email();
    this.organizationId = UserSession.organizationId();
    this.siteId = UserSession.siteId();

    this.roles = UserSession.roles();
  }

  private String id;
  private String firstName;
  private String lastName;
  private String fullName;
  private String email;
  private String organizationId;
  private String siteId;
  private List<String> roles;

}
