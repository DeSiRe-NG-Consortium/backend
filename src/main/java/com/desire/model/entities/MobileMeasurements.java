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

package com.desire.model.entities;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.querydsl.core.annotations.QueryInit;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * Mobile measurements taken by phone application regarding network capabilities and signal strength
 */
@Data
@FieldNameConstants
@Document(collection = "measurements")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class MobileMeasurements {

  @Id
  @EqualsAndHashCode.Include
  private String id = UUID.randomUUID().toString();

  @NotNull
  @QueryInit("site.organization")
  private Campaigns campaign;

  /**
   * When the measurement was taken
   */
  @NotNull
  private LocalDateTime measureMoment;
  
  /**
   * Time it takes for a data packet to travel from source to destination (ms)
   */
  @NotNull
  private float latency;
  
  /**
   * Rate at which video content is uploaded (Mbps)
   */
  @NotNull
  private float uploadThroughput;

  /**
   * Actual rate of successful data transfer on Downlink channel (Mbps/Gbps)
   */
  @NotNull
  private float downloadThroughput;
  
  /**
   * Variability in time delay between packets arriving (ms)
   */
  @NotNull
  private float jitter;
  
  /**
   * dBm
   */
  @NotNull
  private Integer ssRsrp;
  
  /**
   * Round trip time for a message sent from the originating to a target device and back (ms)
   */
  private float ping;

  /**
   * Proportion of packets that are lost in transmission versus those sent (ms)
   */
  private float packetLoss;

  /**
   * Proportion of time the network is functioning properly over a specific period (%)
   */
  private float networkAvailability;

  /**
   * Ratio of the number of errors per number of total bits sent (ratio)
   */
  private float bitErrorRate;

  /**
   * Ratio of frames with errors to total number of frames transmitted (ratio or %)
   */
  private float frameErrorRate;

  /**
   * Number of devices per square kilometer that can be connected without performance degradation
   * (devices/km²)
   */
  private float connectionDensity;

  /**
   * Metrics related to the network's handling of device movement (e.g., handover success rate, %)
   */
  private float mobilityPerformance;

}
