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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.desire.constants.Roles;
import com.desire.dtos.AGVDto;
import com.desire.dtos.SearchAGVPositionRequest;
import com.desire.dtos.SearchAGVRequest;
import com.desire.model.repositories.SiteRepository;
import com.desire.utils.Constants.UserAttributes;
import com.desire.utils.KeycloakService;
import com.desire.utils.UserSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AGVService {

  private final @NonNull AGVPositionService agvPositionService;
  private final @NonNull AGVStatusService agvStatusService;
  private final @NonNull KeycloakService keycloakService;
  private final @NonNull SiteRepository siteDao;

  public Page<AGVDto> search(SearchAGVRequest request) {
    List<UserRepresentation> agvUsers = getFilteredKeycloakUsers(request);

    if (agvUsers.isEmpty()) {
      return Page.empty();
    }

    List<AGVDto> agvDtos = agvUsers.stream().map(this::buildDto).toList();

    if (Boolean.TRUE.equals(request.getOnline())) {
      agvDtos = agvDtos.stream().filter(AGVDto::isOnline).toList();
    } else if (Boolean.FALSE.equals(request.getOnline())) {
      agvDtos = agvDtos.stream().filter(agvDto -> !agvDto.isOnline()).toList();
    }

    if (Boolean.TRUE.equals(request.getIdle())) {
      agvDtos = agvDtos.stream().filter(agvDto -> agvDto.getCurrentCampaign() == null).toList();
    } else if (Boolean.FALSE.equals(request.getIdle())) {
      agvDtos = agvDtos.stream().filter(agvDto -> agvDto.getCurrentCampaign() != null).toList();
    }

    if (Direction.DESC.equals(request.getSortDirection())) {
      agvDtos = agvDtos.stream().sorted(Comparator.comparing(AGVDto::getId).reversed()).toList();
    } else {
      agvDtos = agvDtos.stream().sorted(Comparator.comparing(AGVDto::getId)).toList();
    }

    int totalSize = agvUsers.size();

    agvDtos = agvDtos.stream().skip((long) request.getPageNumber() * request.getPageSize())
        .limit(request.getPageSize()).toList();

    return new PageImpl<>(agvDtos, request.getPage(AGVDto.Fields.id), totalSize);
  }

  private AGVDto buildDto(UserRepresentation keycloakUser) {
    AGVDto agvDto = new AGVDto();

    List<String> siteIds = keycloakUser.getAttributes().get(UserAttributes.SITE_ID);

    agvDto.setId(keycloakUser.getId());
    if (!CollectionUtils.isEmpty(siteIds)) {
      agvDto.setSites(siteDao.findAllByIdIn(siteIds));
    }
    agvDto.setOnline(agvStatusService.isAgvOnline(keycloakUser.getId()));
    agvDto.setCurrentCampaign(agvStatusService.getCurrentCampaign(keycloakUser.getId()));
    agvDto.setLatestPosition(agvPositionService
        .search(SearchAGVPositionRequest.builder().agvId(keycloakUser.getId()).build()).stream()
        .findFirst().orElse(null));
    agvDto.setLatestStatusEvent(agvStatusService.getLatestStatusEvent(keycloakUser.getId()));

    return agvDto;
  }

  private List<UserRepresentation> getFilteredKeycloakUsers(SearchAGVRequest request) {
    // Either look for single user or for all.
    if (StringUtils.isNotBlank(request.getId())) {
      Optional<UserRepresentation> agvUserOptional = keycloakService.getUserById(request.getId());
      // Only continue if requested AGV user exists in Keycloak.
      if (agvUserOptional.isPresent()
          && keycloakService.getRoles(request.getId()).contains(Roles.AGV)) {
        UserRepresentation agvUser = agvUserOptional.get();

        // If site ID is requested, check if found user has that attribute.
        if (StringUtils.isNotBlank(request.getSiteId()) && !StringUtils.equals(request.getSiteId(),
            keycloakService.getAttribute(agvUser, UserAttributes.SITE_ID))) {
          return List.of();
        }

        return List.of(agvUser);
      } else {
        return List.of();
      }
    } else {
      return keycloakService.search(Roles.AGV, UserSession.organizationId(), request.getSiteId());
    }
  }
}
