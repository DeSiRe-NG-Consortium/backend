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

/**
 * Centralizes all the endpoints paths used by the application.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Endpoints {

  // Internal application's endpoints
  public static final String SPRING_ADMIN = "/admincp/**"; // Spring Admin
  public static final String SPRING_ADMIN_REGISTRATION_URL = "/admincp/instances";
  public static final String ACTUATOR = "/actuator/**";
  public static final String SWAGGER_UI = "/swagger-ui/**";

  private static final String TAG_AGVS = "AGVs";
  private static final String TAG_ORGANIZATIONS = "Organizations";
  private static final String TAG_TUI = "TUI Integration";

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Campaigns {
    public static final String PATH = "/campaigns";

    public static final String TAG = "Measurement Campaigns";

    public static final String ID = "/{campaignId}";

    public static final String INTERPOLATED_MEASUREMENTS = ID + "/interpolated-measurements";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class AGV {
    public static final String PATH = "/agv";

    public static final String TAG = "AGV";

    public static final String STATUS_EVENTS = "/status-events";

    public static final String COMMANDS = "/commands";

    public static final String COMMANDS_STREAM = COMMANDS + "/stream";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class AGVMeasurements {
    public static final String PATH = "/agv-measurements";

    public static final String TAG = "AGV Measurements";
  }


  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class AGVPositions {
    public static final String PATH = "/agv-positions";

    public static final String TAG = "AGV Positions";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class AGVs {
    public static final String PATH = "/agvs";

    public static final String TAG = TAG_AGVS;

    public static final String ID = "/{agvId}";

    public static final String STREAM = ID + "/stream";

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StatusEvents {
      public static final String PATH = AGVs.PATH + ID + AGV.STATUS_EVENTS;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Commands {
      public static final String PATH = AGVs.PATH + AGVs.ID + AGV.COMMANDS;

      public static final String ID = "/{commandId}";
    }
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class MobileMeasurements {
    public static final String PATH = "/mobile-measurements";

    public static final String TAG = "Mobile Measurements";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Organizations {
    public static final String PATH = "/organizations";

    public static final String TAG = TAG_ORGANIZATIONS;

    public static final String ID = "/{organizationId}";

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Sites {
      public static final String PATH = Organizations.PATH + Organizations.ID + "/sites";

      public static final String ID = "/{siteId}";
    }
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class TUI {
    public static final String TAG = TAG_TUI;

    public static final String AGV_POSITIONS = "/agv-positions";
    public static final String CAMPAIGN = "/campaigns";
    public static final String CAMPAIGN_RESULTS = "/campaignResults";
    public static final String INTERPOLATED_MEASUREMENTS_BULK = "/interpolateBulk";

    @Deprecated(forRemoval = false)
    public static final String FAKE_TUI_CAMPAIGN_ID_ENDPOINT = CAMPAIGN + "{id}";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class TuiMessages {
    public static final String PATH = "/tui-messages";

    public static final String TAG = TAG_TUI;

    public static final String CAMPAIGN = "/campaigns";
    public static final String AGV_POSITIONS = "/agv-positions";

    public static final String TUI_SYNC_CAMPAIGN = CAMPAIGN + "/queue";
    public static final String TUI_SYNC_AGV_POSITIONS = AGV_POSITIONS + "/queue";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class User {
    public static final String PATH = "/user";

    public static final String TAG = "User";
  }

}
