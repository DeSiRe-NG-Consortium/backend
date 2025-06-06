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

package com.desire.services;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.desire.constants.Endpoints;
import com.desire.model.entities.TuiCampaignMessages;
import com.desire.model.repositories.TuiCampaignMessageRepository;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TuiCampaignService extends TuiIntegrationBaseService<TuiCampaignMessages> {

  @Value("${TUI_BACKEND_URL}" + Endpoints.TUI.CAMPAIGN)
  private String tuiBackendUrl;

  private final @NonNull TuiCampaignMessageRepository tuiCampaignDao;
  private final @NonNull RestTemplate restTemplate;

  public void sendMessage(TuiCampaignMessages campaignMessage) throws Exception {
    String campaignData = toJson(campaignMessage.getCampaign());

    if (StringUtils.isNotBlank(campaignData)) {
      try {
        log.info("Sending campaign message to TUI backend: {} {}", campaignMessage.getHttpMethod(),
            tuiBackendUrl);

        // Make the HTTP call using the method and campaign data
        ResponseEntity<Void> response = restTemplate.exchange(tuiBackendUrl,
            HttpMethod.valueOf(campaignMessage.getHttpMethod()),
            new HttpEntity<>(campaignData, getDefaultHeaders()), Void.class);

        // Handle the response
        if (response.getStatusCode().is2xxSuccessful()) {
          success(campaignMessage);
        } else {
          Exception exception = new Exception("HTTP error status: " + response.getStatusCode());
          failed(campaignMessage, exception);
          throw exception;
        }
      } catch (Exception e) {
        failed(campaignMessage, e);
        throw e;
      }
    }
  }

  protected void success(TuiCampaignMessages failedMessage) {
    tuiCampaignDao.delete(failedMessage);
  }

  protected void failed(TuiCampaignMessages failedMessage, Throwable throwable) {
    failedMessage.setLatestAttemptMoment(LocalDateTime.now());
    failedMessage.getErrors().add(getErrorLine(throwable));

    tuiCampaignDao.save(failedMessage);
  }

}
