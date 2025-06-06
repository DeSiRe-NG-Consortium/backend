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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.desire.model.entities.TuiAGVPositionMessages;
import com.desire.model.entities.TuiCampaignMessages;
import com.desire.model.repositories.TuiAGVPositionMessageRepository;
import com.desire.model.repositories.TuiCampaignMessageRepository;
import com.desire.services.TuiAGVPositionService;
import com.desire.services.TuiCampaignService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every change we make on a campaign or AGV position will be sent to TUI's backend for data
 * synchronization
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TuiDataPushTask {

  private final Lock lock = new ReentrantLock();

  private final @NonNull TuiCampaignService tuiCampaignService;
  private final @NonNull TuiCampaignMessageRepository tuiCampaignDao;
  private final @NonNull TuiAGVPositionService tuiPositionService;
  private final @NonNull TuiAGVPositionMessageRepository tuiPositionDao;

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
  public void tuiDataSynchronization() {
    if (lock.tryLock()) {
      log.info("TUI data synchronization task started.");
      try {
        campaignSynchronization();
        agvPositionSynchronization();
        log.info("TUI data synchronization task finished.");
      } catch (Exception e) {
        log.error("Error synchronizing data with TUI backend", e);
      } finally {
        lock.unlock();
      }
    } else {
      log.info("TUI data synchronization task is already running.");
    }
  }

  @Async
  public void executeAsync() {
    tuiDataSynchronization();
  }

  private void campaignSynchronization() throws Exception {
    log.info("TUI campaign synchronization task started.");

    try {
      List<TuiCampaignMessages> pendingMessages = tuiCampaignDao.findAllByOrderByCreateMomentAsc();

      log.info("TUI campaigns pending to be sent: {}", pendingMessages.size());

      for (TuiCampaignMessages msg : pendingMessages) {
        tuiCampaignService.sendMessage(msg);
      }
    } catch (Exception e) {
      log.error("Error sending campaign message to TUI backend");
      throw e;
    }

    log.info("TUI campaign synchronization task finished.");
  }

  private void agvPositionSynchronization() throws Exception {
    log.info("TUI AGV positions synchronization task started.");

    try {
      List<TuiAGVPositionMessages> pendingMessages =
          tuiPositionDao.findAllByOrderByCreateMomentAsc();

      log.info("TUI positions pending to be sent: {}", pendingMessages.size());

      for (TuiAGVPositionMessages msg : pendingMessages) {
        tuiPositionService.sendMessage(msg);
      }
    } catch (Exception e) {
      log.error("Error sending AGV position message to TUI backend");
      throw e;
    }

    log.info("TUI AGV positions synchronization task finished.");
  }

}
