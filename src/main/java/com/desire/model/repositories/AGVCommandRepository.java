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

package com.desire.model.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.Campaigns;
import com.desire.types.AGVCommandState;

public interface AGVCommandRepository
    extends MongoRepository<AGVCommands, String>, QuerydslPredicateExecutor<AGVCommands> {

  Optional<AGVCommands> findFirstByAgvIdOrderByCreateMomentDesc(String agvId);

  List<AGVCommands> findByCreateMomentBeforeAndStateIn(LocalDateTime createMomentBefore,
      Set<AGVCommandState> states);

  /**
   * <p>
   * Find the oldest event with the given states.
   * </p>
   *
   * <p>
   * This can be used to find the “next” pending event by passing the states
   * {@link AGVCommands#PENDING_STATES}.
   * </p>
   *
   * @param agvId AGV ID
   * @param states Set of states
   * @return Oldest event with given states for AGV or {@link Optional#empty()}.
   */
  Optional<AGVCommands> findFirstByAgvIdAndStateInOrderByCreateMomentAsc(String agvId,
      Set<AGVCommandState> states);

  List<AGVCommands> findByAgvIdAndStateIn(String agvId, Set<AGVCommandState> states);

  List<AGVCommands> findByAgvIdAndCampaignAndStateIn(String agvId, Campaigns campaign,
      Set<AGVCommandState> states);
}
