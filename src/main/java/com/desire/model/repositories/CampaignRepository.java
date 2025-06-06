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
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import com.desire.model.entities.Campaigns;
import com.desire.types.CampaignState;

public interface CampaignRepository
    extends MongoRepository<Campaigns, String>, QuerydslPredicateExecutor<Campaigns> {

  List<Campaigns> findByUpdateMomentAfter(LocalDateTime updateMomentAfter);

  List<Campaigns> findByState(CampaignState state);

  boolean existsByStateAndConfigurationsClientId(CampaignState state, String clientId);

  boolean existsByStateAndConfigurationsEndpointId(CampaignState state, String endpointId);

  boolean existsByStateAndConfigurationsAgvId(CampaignState state, String agvId);

}
