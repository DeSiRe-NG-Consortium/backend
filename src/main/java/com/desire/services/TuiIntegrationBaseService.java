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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Slf4j
public abstract class TuiIntegrationBaseService<T> {

  @Autowired
  ObjectMapper objectMapper;

  /**
   * Sends a message to TUI backend application to synchronize our data with their database.
   * 
   * @param message
   * @throws Exception
   */
  protected abstract void sendMessage(T message) throws Exception;

  /**
   * Method triggered when the message is sent successfully.
   * 
   * @param failedMessage
   */
  protected abstract void success(T failedMessage);

  /**
   * Method triggered when the message failed to be sent.
   * 
   * @param failedMessage
   * @param throwable Contains the error data
   */
  protected abstract void failed(T failedMessage, Throwable throwable);

  /**
   * Creates a debug error line for failed messages.
   * 
   * @param throwable Exception thrown when calling TUI backend services
   * @return Error line to add to message error history.
   */
  protected String getErrorLine(Throwable throwable) {
    return String.join(" – ", LocalDateTime.now().toString(), throwable.getMessage());
  }

  /**
   * Converts an object to JSON
   * 
   * @param object
   * @return Serialized object or empty String if an error occurred.
   */
  protected String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      log.error("failed to convert to JSON: {}", object.toString());
      return "";
    }
  }

  protected HttpHeaders getDefaultHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    return headers;
  }

}
