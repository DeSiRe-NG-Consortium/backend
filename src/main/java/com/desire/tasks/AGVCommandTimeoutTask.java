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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import com.desire.model.entities.AGVCommands;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.types.AGVCommandState;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVCommandTimeoutTask {

  private final @NonNull AGVCommandRepository agvCommandDao;

  public static final Duration TIMEOUT_THRESHOLD = Duration.ofDays(1);

  @Scheduled(fixedRate = 60L, timeUnit = TimeUnit.MINUTES)
  public void processTimeouts() {
    log.debug("AGV command timeout task started.");

    LocalDateTime timeoutThreshold = LocalDateTime.now().minus(TIMEOUT_THRESHOLD);

    List<AGVCommands> timeoutCommands =
        agvCommandDao.findByCreateMomentBeforeAndStateIn(timeoutThreshold, AGVCommands.OPEN_STATES);

    for (AGVCommands agvCommand : timeoutCommands) {
      agvCommand.setState(AGVCommandState.TIMEOUT);
    }

    if (!CollectionUtils.isEmpty(timeoutCommands)) {
      log.info("{} AGV commands timed out.", timeoutCommands.size());

      agvCommandDao.saveAll(timeoutCommands);
    }

    log.debug("AGV command timeout task finished.");
  }
}
