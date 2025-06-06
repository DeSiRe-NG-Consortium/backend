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

import com.desire.model.entities.Campaigns;
import com.desire.model.repositories.CampaignRepository;
import com.desire.services.AGVMeasurementService;
import com.desire.types.CampaignState;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TuiDataPullTask {

  private final @NonNull AGVMeasurementService measurementService;
  private final @NonNull CampaignRepository campaignDao;

  private final Lock lock = new ReentrantLock();

  @Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
  public void onlinePull() {
    if (lock.tryLock()) {
      try {
        List<Campaigns> campaigns = campaignDao.findByState(CampaignState.RUNNING);

        if (!campaigns.isEmpty()) {
          log.info("Running TUI data online synchronization.");

          campaigns.forEach(campaign -> measurementService.pullData(campaign, false));

          log.info("TUI data online synchronization finished.");
        }
      } catch (Exception e) {
        log.error("Error synchronizing data with TUI backend in online mode.", e);
      } finally {
        lock.unlock();
      }
    }
  }

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void offlinePull() {
    if (lock.tryLock()) {
      try {
        log.info("Running TUI data offline synchronization.");

        campaignDao.findAll().forEach(campaign -> measurementService.pullData(campaign, true));

        log.info("TUI data offline synchronization finished.");
      } catch (Exception e) {
        log.error("Error synchronizing data with TUI backend in offline mode.", e);
      } finally {
        lock.unlock();
      }
    }
  }
}
