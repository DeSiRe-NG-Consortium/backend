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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.QAGVCommands;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.types.AGVCommandState;
import com.desire.utils.Pagination;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVCommandStreamService extends EventStreamService<String, AGVCommands> {

  private final @NonNull AGVCommandRepository agvCommandDao;

  @Override
  public void publish(String agvId, AGVCommands event) {
    if (event.isCreated()) {
      event.setState(AGVCommandState.SENT);
      event.setUpdateMoment(LocalDateTime.now());
    }

    event.setLatestSendMoment(LocalDateTime.now());

    agvCommandDao.save(event);

    super.publish(agvId, event);

    // TODO: Set AGV to offline if messages cannot be published.
  }

  protected void sendHeartbeat(String agvId, ResponseBodyEmitter eventStream) throws IOException {
    Optional<AGVCommands> pendingEventOptional =
        findLatestByState(agvId, List.of(AGVCommandState.SENT));

    if (pendingEventOptional.isPresent()) {
      publish(agvId, pendingEventOptional.get());
    } else {
      super.sendHeartbeat(eventStream);
    }

    // TODO: Set AGV to offline if messages cannot be published.
  }

  /**
   * <p>
   * Find the latest event with the given states which has already been sent.
   * </p>
   *
   * @param agvId AGV ID
   * @return Oldest event with given states for AGV or {@link Optional#empty()}.
   */
  public Optional<AGVCommands> findLatestByState(String agvId, List<AGVCommandState> states) {
    BooleanBuilder query = new BooleanBuilder();

    QAGVCommands qCommands = QAGVCommands.aGVCommands;

    query.and(qCommands.agvId.eq(agvId));

    query.and(qCommands.state.in(states));

    query.and(qCommands.latestSendMoment.isNotNull());

    Pageable singleRowPage = Pagination.ONE_LATEST.getPage(AGVCommands.Fields.latestSendMoment);
    Page<AGVCommands> page = agvCommandDao.findAll(query, singleRowPage);

    return page.get().findFirst();
  }

  @Override
  public ResponseBodyEmitter subscribe(String id) {
    ResponseBodyEmitter newEventStream = super.subscribe(id);

    // Send the latest command to the AGV
    Optional<AGVCommands> latestCommand = agvCommandDao.findFirstByAgvIdOrderByCreateMomentDesc(id);
    latestCommand.ifPresent(agvCommands -> super.publish(id, agvCommands));

    return newEventStream;
  }

}
