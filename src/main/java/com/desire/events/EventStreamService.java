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

package com.desire.events;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Provides streams. Streams are grouped and addressed by keys of type {@link K}.
 * </p>
 *
 * <p>
 * This service works in a way that for each single {@code GET} request a new response body emitter
 * is created that will live for the request lifetime or – in case of inactivity or client
 * disconnects – up to {@link #TIMEOUT} before it gets closed and removed.
 * </p>
 *
 * @param <K> Type of stream keys
 * @param <V> Type of object to publish on stream
 */
@Slf4j
@Service
public abstract class EventStreamService<K, V> {

  public record Heartbeat(LocalDateTime dateTime) {

  }

  @Autowired
  ObjectMapper objectMapper;

  private static final String NEW_LINE = "\n";
  private static final Duration TIMEOUT = Duration.ofHours(8);

  protected final ConcurrentHashMap<K, List<ResponseBodyEmitter>> streams =
      new ConcurrentHashMap<>();

  /**
   * Returns a new response body emitter for the given key.
   *
   * @param key Target stream group
   * @return New response body emitter.
   */
  public ResponseBodyEmitter subscribe(K key) {
    if (key == null) {
      return null;
    }

    ResponseBodyEmitter newEventStream = null;

    try {
      newEventStream = createEventStream(key);

      sendHeartbeat(newEventStream);
    } catch (Exception e) {
      log.error("Getting stream for key {} with error '{}'.", key, e.getMessage());
    }

    return newEventStream;
  }

  /**
   * Sends an event to all streams associated to the given key.
   *
   * @param key Target stream group
   * @param eventObject Object to be published in target stream
   */
  public void publish(K key, V eventObject) {
    streams.computeIfPresent(key, (k, eventStreamsList) -> {
      eventStreamsList.forEach(eventStream -> {
        try {
          eventStream.send(objectMapper.writeValueAsString(eventObject).concat(NEW_LINE));
        } catch (Exception e) {
          log.error("Sending event for key {} with error '{}'.", key, e.getMessage());
        }
      });

      return eventStreamsList;
    });
  }

  /**
   * Completes all the streams associated to the given key and removes the key from the list.
   *
   * @param key Target stream group
   */
  public void close(K key) {
    streams.computeIfPresent(key, (k, streamsList) -> {
      streamsList.forEach(ResponseBodyEmitter::complete);

      return null;
    });
  }

  /**
   * Completes the specific stream and removes it from the list.
   *
   * @param key Target stream group
   * @param eventStream response body emitter to be closed
   */
  private void close(K key, ResponseBodyEmitter eventStream) {
    streams.computeIfPresent(key, (k, streamsList) -> {
      eventStream.complete();
      streamsList.remove(eventStream);

      // Check if we just deleted the last item of the list
      if (CollectionUtils.isEmpty(streamsList)) {
        log.info("Removing event stream list for key {}.", key);
        return null; // Deletes the list for associated key
      }

      return streamsList;
    });
  }

  /**
   * Creates a new response body stream and adds it to the list of associated streams.
   *
   * @param key Target stream group
   * @return New response body emitter.
   */
  protected ResponseBodyEmitter createEventStream(K key) {
    ResponseBodyEmitter newEventStream = new ResponseBodyEmitter(TIMEOUT.toMillis());

    // Setting up events callback triggers
    newEventStream.onError(error -> {
      log.error("Event error triggered for key {} with error '{}'.", key, error.getMessage());
      close(key, newEventStream);
    });

    newEventStream.onTimeout(() -> {
      log.info("Event timeout triggered for key {}.", key);
      close(key, newEventStream);
    });

    newEventStream.onCompletion(() -> {
      log.info("Event completion triggered for key {}.", key);
      close(key, newEventStream);
    });

    addStreamToList(key, newEventStream);

    return newEventStream;
  }

  /**
   * Adds a new response body stream to the list associated with the map key.
   *
   * @param key Target stream group
   * @param newEventStream New response body emitter
   */
  private void addStreamToList(K key, ResponseBodyEmitter newEventStream) {
    try {
      // Initialize the list if not present already.
      streams.computeIfAbsent(key, k -> new ArrayList<>());

      // Add new event stream to the list.
      streams.computeIfPresent(key, (k, streamsList) -> {
        streamsList.add(newEventStream);

        return streamsList;
      });
    } catch (Exception e) {
      log.error("Adding stream to list for key {} with error '{}'.", key, e.getMessage());
    }
  }

  /**
   * Sends a message containing the current time to let know the client stream started. Clients
   * might use this timestamp to verify or sync their local system time.
   *
   * @param key Key/ID of target stream
   * @param eventStream Target stream
   * @throws IOException Thrown if an error occurs when sending the event.
   */
  protected void sendHeartbeat(ResponseBodyEmitter eventStream) throws IOException {
    eventStream
        .send(objectMapper.writeValueAsString(new Heartbeat(LocalDateTime.now())).concat(NEW_LINE));
  }

  /**
   * Sends regular heartbeat messages to all active response body emitters to keep the connection
   * alive.
   */
  @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
  protected void sendHeartbeats() {
    LocalDateTime startTime = LocalDateTime.now();

    int heartbeatsSent = 0;

    for (Map.Entry<K, List<ResponseBodyEmitter>> stream : streams.entrySet()) {
      for (ResponseBodyEmitter emitter : stream.getValue()) {
        try {
          sendHeartbeat(emitter);
          heartbeatsSent++;
        } catch (IOException e) {
          log.error("Error sending heartbeat message: {}", e.getMessage());
        }
      }
    }

    if (heartbeatsSent > 0) {
      log.info("Sent heartbeats to {} emitters in {} ms.", heartbeatsSent,
          ChronoUnit.MILLIS.between(startTime, LocalDateTime.now()));
    }
  }
}
