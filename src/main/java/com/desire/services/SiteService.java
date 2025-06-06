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

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.desire.dtos.PostOrPatchSiteRequest;
import com.desire.dtos.SearchSiteRequest;
import com.desire.model.entities.Organizations;
import com.desire.model.entities.QSites;
import com.desire.model.entities.Sites;
import com.desire.model.repositories.OrganizationRepository;
import com.desire.model.repositories.SiteRepository;
import com.desire.utils.UserSession;
import com.desire.validations.exceptions.ValidationException;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SiteService {

  private final @NonNull OrganizationRepository organizationDao;
  private final @NonNull SiteRepository siteDao;
  private final @NonNull SiteValidationService siteValidationService;

  public Page<Sites> search(String organizationId, SearchSiteRequest request) {
    BooleanBuilder query = new BooleanBuilder();
    QSites qSites = QSites.sites;

    query.and(qSites.organization.id.eq(UserSession.organizationId()));
    query.and(qSites.organization.id.eq(organizationId));

    if (StringUtils.isNotBlank(request.getId())) {
      query.and(qSites.id.eq(request.getId()));
    }

    if (StringUtils.isNotBlank(request.getName())) {
      query.and(qSites.name.eq(request.getName()));
    }

    if (request.getAvailable() != null) {
      query.and(qSites.available.eq(request.getAvailable()));
    }

    return siteDao.findAll(query, request.getPage(Sites.Fields.createMoment));
  }

  public Sites post(String organizationId, PostOrPatchSiteRequest request)
      throws ValidationException {
    siteValidationService.validatePost(organizationId);

    Optional<Organizations> organizationOptional = organizationDao.findById(organizationId);

    if (organizationOptional.isPresent()) {
      return siteDao.save(request.toEntity(organizationOptional.get()));
    } else {
      log.info("Cannot create site due to invalid organization ID '{}'.", organizationId);
      return null;
    }
  }

  public Sites patch(String organizationId, String siteId, PostOrPatchSiteRequest request)
      throws ValidationException {
    Optional<Sites> siteOptional =
        search(organizationId, SearchSiteRequest.builder().id(siteId).build()).get().findFirst();

    siteValidationService.validateEdit(siteOptional);

    return siteDao.save(request.updateEntity(siteOptional.get()));
  }

  public void delete(String organizationId, String siteId) throws ValidationException {
    Optional<Sites> siteOptional =
        search(organizationId, SearchSiteRequest.builder().id(siteId).build()).get().findFirst();

    siteValidationService.validateEdit(siteOptional);

    siteDao.delete(siteOptional.get());
  }
}
