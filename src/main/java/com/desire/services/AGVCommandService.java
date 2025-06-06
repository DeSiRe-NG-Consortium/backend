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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.desire.dtos.PostAgvCommandRequest;
import com.desire.dtos.SearchAGVCommandRequest;
import com.desire.events.AGVCommandStreamService;
import com.desire.model.entities.AGVCommands;
import com.desire.model.entities.AGVCommands.Fields;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.QAGVCommands;
import com.desire.model.repositories.AGVCommandRepository;
import com.desire.model.repositories.CampaignRepository;
import com.desire.types.AGVCommandState;
import com.desire.types.AGVCommandType;
import com.desire.utils.Pagination;
import com.desire.validations.AGVIdValidator;
import com.desire.validations.exceptions.ValidationException;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVCommandService {

  private final @NonNull AGVCommandRepository agvCommandDao;
  private final @NonNull AGVCommandStreamService agvCommandStream;
  private final @NonNull AGVCommandValidationService agvCommandValidationService;
  private final @NonNull AGVStatusService agvStatusService;
  private final @NonNull AGVIdValidator agvIdValidator;
  private final @NonNull CampaignRepository campaignDao;

  public Page<AGVCommands> search(String agvId, SearchAGVCommandRequest request) {
    if (!agvIdValidator.isValid(agvId, null)) {
      return Page.empty();
    }

    BooleanBuilder query = new BooleanBuilder();

    QAGVCommands qCommands = QAGVCommands.aGVCommands;

    query.and(qCommands.agvId.eq(agvId));

    if (request.getCampaignId() != null) {
      query.and(qCommands.campaign.id.eq(request.getCampaignId()));
    }

    if (request.getState() != null) {
      query.and(qCommands.state.eq(request.getState()));
    }

    if (Boolean.TRUE.equals(request.getPending())) {
      query.and(
          qCommands.state.eq(AGVCommandState.CREATED).or(qCommands.state.eq(AGVCommandState.SENT)));
    }

    if (request.getCommandId() != null) {
      query.and(qCommands.id.eq(request.getCommandId()));
    }

    return agvCommandDao.findAll(query, request.getPage(AGVCommands.Fields.measureMoment));
  }

  public AGVCommands post(String agvId, PostAgvCommandRequest request) throws ValidationException {
    agvCommandValidationService.validatePost(agvId, request);

    Campaigns campaign = campaignDao.findById(request.getCampaignId()).orElse(null);

    if (campaign == null) {
      return null;
    }

    return saveAndScheduleCommand(request.toEntity(agvId, campaign));
  }

  public AGVCommands patch(String agvId, String commandId, AGVCommandState state)
      throws ValidationException {
    Optional<AGVCommands> commandOptional = agvCommandDao.findById(commandId);

    agvCommandValidationService.validatePatch(agvId, commandOptional);

    AGVCommands command = commandOptional.get();

    command.setState(state);
    command.setUpdateMoment(LocalDateTime.now());

    return saveAndScheduleCommand(command, AGVCommandState.ABORTED.equals(state));
  }

  /**
   * Looks up the next pending command for the current campaign of the given AGV and sends that
   * command to the AGV.
   *
   * @param agvId AGV ID
   * @return Scheduled event of {@code null} if there is no pending event for given AGV.
   */
  public AGVCommands scheduleNextCampaignCommand(String agvId) {
    Campaigns currentCampaign = agvStatusService.getCurrentCampaign(agvId);

    // Only publish events if AGV is online and there is a running campaign for that AGV.
    if (agvStatusService.isAgvOnline(agvId) && currentCampaign != null) {
      Optional<AGVCommands> nextCommandOptional = getCurrentOrNextCommand(agvId, currentCampaign);

      // Only schedule “new” commands (i.e., not yet sent).
      if (nextCommandOptional.isPresent() && nextCommandOptional.get().isCreated()) {
        AGVCommands nextCommand = nextCommandOptional.get();

        log.info("Scheduling {} as next command for campaign {} and AGV {}.", nextCommand.getType(),
            nextCommand.getCampaign().getId(), agvId);

        agvCommandStream.publish(nextCommand.getAgvId(), nextCommand);

        // Return the command from the DB after is has been sent, updating its status.
        return agvCommandDao.findById(nextCommand.getId()).orElse(null);
      }
    }

    return null;
  }

  /**
   * <p>
   * Saves the given command to the DB and then schedules the next command for the AGV referenced in
   * the given AGV.
   * </p>
   *
   * <p>
   * The next scheduled command may not be the given one due to the scheduling logic. E.g., a
   * command for a new campaign would only be scheduled once the current campaign has been
   * completed. This can be overridden with the {@code overrideScheduling} parameter.
   * </p>
   *
   * @param command Command to be saved
   * @param overrideScheduling If {@code true}, command will be sent to the AGV directly, overriding
   *        the scheduling logic
   * @return Command as persisted in DB, possibly sent to the AGV.
   */
  public AGVCommands saveAndScheduleCommand(AGVCommands command, boolean overrideScheduling) {
    command = agvCommandDao.save(command);

    if (overrideScheduling && agvStatusService.isAgvOnline(command.getAgvId())) {
      log.info("Scheduling {} command with override for AGV {}.", command.getType(),
          command.getAgvId());

      agvCommandStream.publish(command.getAgvId(), command);
    }

    tryCreateResumeCampaignCommand(command);

    scheduleNextCampaignCommand(command.getAgvId());

    return agvCommandDao.findById(command.getId()).orElse(null);
  }

  /**
   * Calls {@link #saveAndScheduleCommand(AGVCommands, boolean)} with {@code overrideScheduling} set
   * to {@code false}.
   *
   * @param command Command to be saved
   * @return Command as persisted in DB, possibly sent to the AGV.
   */
  public AGVCommands saveAndScheduleCommand(AGVCommands command) {
    return saveAndScheduleCommand(command, false);
  }

  /**
   * <p>
   * Cancels all pending commands for the given campaign.
   * </p>
   *
   * <p>
   * If the to-be-cancelled include a command that is currently being processed by the AGV, this
   * command will be explicitly aborted by sending the aborted command to the AGV.
   * </p>
   *
   * <p>
   * If the given campaign is {@code null}, all pending commands for th given AGV will be cancelled.
   * </p>
   *
   * @param agvId AGV ID
   * @param campaign Campaign to be closed, nullable
   */
  public void cancelPendingCommands(String agvId, Campaigns campaign) {
    // Explicitly cancel the currently pending command by sending the aborted command to the AGV.
    Optional<AGVCommands> pendingCommandOptional = getCurrentOrNextCommand(agvId);

    if (pendingCommandOptional.isPresent() && campaign != null
        && campaign.getId().equals(pendingCommandOptional.get().getCampaign().getId())) {
      AGVCommands command = setCommandAborted(pendingCommandOptional.get());

      log.info("Aborting current command {} for AGV {}.", command.getType(), command.getAgvId());

      agvCommandStream.publish(command.getAgvId(), command);
    }

    if (campaign != null) {
      log.info("Aborting commands for AGV {} and campaign {}.", agvId, campaign.getId());

      // Cancel all other pending commands for the AGV and the given campaign without sending them
      // to the AGV.
      agvCommandDao.findByAgvIdAndCampaignAndStateIn(agvId, campaign, AGVCommands.PENDING_STATES)
          .forEach(this::setCommandAborted);
    } else {
      log.info("Aborting all commands for AGV {}.", agvId);

      // Cancel all other pending commands for the AGV without sending them to the AGV.
      agvCommandDao.findByAgvIdAndStateIn(agvId, AGVCommands.PENDING_STATES)
          .forEach(this::setCommandAborted);
    }
  }

  private AGVCommands setCommandAborted(AGVCommands command) {
    command.setState(AGVCommandState.ABORTED);
    command.setUpdateMoment(LocalDateTime.now());

    return agvCommandDao.save(command);
  }

  private Optional<AGVCommands> getCurrentOrNextCommand(String agvId) {
    Campaigns currentCampaign = agvStatusService.getCurrentCampaign(agvId);

    if (currentCampaign != null) {
      Optional<AGVCommands> nextCommandOptional = getCurrentOrNextCommand(agvId, currentCampaign);

      if (nextCommandOptional.isPresent()) {
        return nextCommandOptional;
      }
    }

    return getCurrentOrNextCommand(agvId, null);
  }

  private Optional<AGVCommands> getCurrentOrNextCommand(String agvId, Campaigns campaign) {
    BooleanBuilder query = new BooleanBuilder();

    QAGVCommands qCommands = QAGVCommands.aGVCommands;

    query.and(qCommands.agvId.eq(agvId));

    query.and(
        qCommands.state.eq(AGVCommandState.CREATED).or(qCommands.state.eq(AGVCommandState.SENT)));

    if (campaign != null) {
      query.and(qCommands.campaign.id.eq(campaign.getId()));
    }

    Page<AGVCommands> page =
        agvCommandDao.findAll(query, Pagination.ONE_OLDEST.getPage(Fields.createMoment));

    return page.get().findFirst();
  }

  /**
   * <p>
   * Evaluates if a command to resume to normal campaign operation should be created. This is the
   * case if the previous command was a now-closed go-to command and there are no other go-to
   * commands for the current campaign of the references AGV.
   * </p>
   *
   * <p>
   * If the evaluation succeeds, a new AGV command will be generated and saved into the DB.
   * </p>
   *
   * @param previousCommand AGV command to evaluate
   */
  private void tryCreateResumeCampaignCommand(AGVCommands previousCommand) {
    if (AGVCommandType.GO_TO.equals(previousCommand.getType()) && previousCommand.isClosed()
        && getCurrentOrNextCommand(previousCommand.getAgvId(), previousCommand.getCampaign())
            .isEmpty()) {
      AGVCommands resumeCommand = new AGVCommands();

      resumeCommand.setCampaign(previousCommand.getCampaign());
      resumeCommand.setAgvId(previousCommand.getAgvId());
      resumeCommand.setType(AGVCommandType.RESUME_CAMPAIGN);
      resumeCommand.setMeasureMoment(LocalDateTime.now());

      agvCommandDao.save(resumeCommand);
    }
  }
}
