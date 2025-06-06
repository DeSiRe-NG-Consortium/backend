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
import com.desire.model.entities.TuiAGVPositionMessages;
import com.desire.model.repositories.TuiAGVPositionMessageRepository;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TuiAGVPositionService extends TuiIntegrationBaseService<TuiAGVPositionMessages> {

  @Value("${TUI_BACKEND_URL}" + Endpoints.TUI.AGV_POSITIONS)
  private String tuiBackendUrl;

  private final @NonNull TuiAGVPositionMessageRepository tuiAgvPositionDao;
  private final @NonNull RestTemplate restTemplate;

  public void sendMessage(TuiAGVPositionMessages positionMessage) throws Exception {
    String positionData = toJson(positionMessage.getPosition());

    if (StringUtils.isNotBlank(positionData)) {

      try {
        log.info("Sending AGV positions message to TUI backend: {} {}", positionData,
            tuiBackendUrl);

        // Make the HTTP call using the method and campaign data
        ResponseEntity<Void> response = restTemplate.exchange(tuiBackendUrl, HttpMethod.POST,
            new HttpEntity<>(positionData, getDefaultHeaders()), Void.class);

        // Handle the response
        if (response.getStatusCode().is2xxSuccessful()) {
          success(positionMessage);
        } else {
          Exception exception = new Exception("HTTP error status: " + response.getStatusCode());
          failed(positionMessage, exception);
          throw exception;
        }
      } catch (Exception e) {
        failed(positionMessage, e);
        throw e;
      }
    }
  }

  protected void success(TuiAGVPositionMessages failedMessage) {
    tuiAgvPositionDao.delete(failedMessage);
  }

  protected void failed(TuiAGVPositionMessages failedMessage, Throwable throwable) {
    failedMessage.setLatestAttemptMoment(LocalDateTime.now());
    failedMessage.getErrors().add(getErrorLine(throwable));

    tuiAgvPositionDao.save(failedMessage);
  }

}
