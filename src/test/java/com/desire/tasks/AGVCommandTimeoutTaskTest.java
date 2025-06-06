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

package com.desire.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.Campaigns;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.types.CampaignState;
import com.desire.utils.BaseMvcTest;

class AGVCommandTimeoutTaskTest extends BaseMvcTest {

  @Autowired
  private AGVCommandRepository agvCommandDao;

  @Autowired
  private AGVCommandTimeoutTask timeoutTask;

  @Test
  void testTimeoutTask() {
    Campaigns campaign =
        testDataService.getOrCreateCampaign("Timeout test", CampaignState.RUNNING, testDataService
            .getOrCreateSite("Test site", testDataService.getOrCreateOrganization("Test Org.")));

    String randomAgvId = UUID.randomUUID().toString();

    AGVCommands command1 =
        testDataService.createAGVCommand(randomAgvId, AGVCommandType.GO_TO, campaign);
    command1.setState(AGVCommandState.SENT);
    agvCommandDao.save(command1);

    AGVCommands command2 =
        testDataService.createAGVCommand(randomAgvId, AGVCommandType.GO_TO, campaign);
    command2.setState(AGVCommandState.SENT);
    command2.setCreateMoment(LocalDateTime.now().minusHours(25));
    agvCommandDao.save(command2);

    timeoutTask.processTimeouts();

    assertTrue(agvCommandDao.findById(command1.getId()).isPresent());
    assertEquals(AGVCommandState.SENT, agvCommandDao.findById(command1.getId()).get().getState());
    assertTrue(agvCommandDao.findById(command2.getId()).isPresent());
    assertEquals(AGVCommandState.TIMEOUT,
        agvCommandDao.findById(command2.getId()).get().getState());
  }
}
