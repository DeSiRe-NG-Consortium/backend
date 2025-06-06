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

import com.desire.constants.Constraints.Text;
import com.desire.model.entities.AGVMeasurements;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.Coordinates;
import com.desire.utils.TuiTimestampDeserializer;
import com.desire.utils.TuiTimestampSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Valid
@NoArgsConstructor
public class PostAGVMeasurementRequest {

  @NotEmpty
  @Pattern(regexp = Text.Pattern.UUID)
  private String campaignId;

  private List<PostAGVMeasurementValueRequest> values;

  @Data
  @Valid
  public static class PostAGVMeasurementValueRequest {

    @NotNull
    @JsonProperty("time")
    @JsonDeserialize(using = TuiTimestampDeserializer.class)
    @JsonSerialize(using = TuiTimestampSerializer.class)
    private ZonedDateTime measureMoment;

    @NotNull
    private Long measurementId;

    @NotNull
    @Pattern(regexp = Text.Pattern.UUID)
    @JsonProperty("campaign_id")
    private String campaignId;

    @NotNull
    @Pattern(regexp = Text.Pattern.UUID)
    @JsonProperty("agv_id")
    private String agvId;

    @NotNull
    @JsonProperty("client_id")
    private String clientId;

    @NotNull
    @JsonProperty("endpoint_id")
    private String endpointId;

    @JsonProperty("datarate")
    private Float dataRate;

    private Float latency;

    private Float locationX;

    private Float locationY;

    private Float locationZ;

    public AGVMeasurements toEntity(Campaigns campaign) {
      AGVMeasurements measurement = new AGVMeasurements();

      measurement.setCampaign(campaign);
      measurement.setLatency(latency);
      measurement.setDataRate(dataRate);
      measurement.setMeasurementId(measurementId);

      Coordinates coordinates = new Coordinates();
      coordinates.setX(locationX);
      coordinates.setY(locationY);
      coordinates.setZ(locationZ);
      coordinates.setMeasureMoment(measureMoment.toLocalDateTime());

      measurement.setCoordinates(coordinates);

      return measurement;
    }
  }
}
