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

import com.desire.model.entities.QMobileMeasurements;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.desire.dtos.PostMobileMeasurementRequest;
import com.desire.dtos.SearchMobileMeasurementRequest;
import com.desire.model.entities.Campaigns;
import com.desire.model.entities.MobileMeasurements;
import com.desire.model.repositories.CampaignRepository;
import com.desire.model.repositories.MobileMeasurementRepository;
import com.desire.utils.UserSession;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import com.querydsl.core.BooleanBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class MobileMeasurementService {

  private final @NonNull MobileMeasurementRepository measurementDao;
  private final @NonNull CampaignRepository campaignDao;

  public Page<MobileMeasurements> search(SearchMobileMeasurementRequest request) {
    BooleanBuilder query = new BooleanBuilder();
    QMobileMeasurements qMeasurement = QMobileMeasurements.mobileMeasurements;

    // filter by user organization
    query.and(qMeasurement.campaign.site.organization.id.eq(UserSession.organizationId()));

    if (StringUtils.isNotBlank(request.getCampaignId())) {
      query.and(qMeasurement.campaign.id.eq(request.getCampaignId()));
    }

    if (request.getFrom() != null) {
      query.and(qMeasurement.measureMoment.goe(request.getFrom()));
    }

    if (request.getTo() != null) {
      query.and(qMeasurement.measureMoment.loe(request.getFrom()));
    }

    return measurementDao.findAll(query, request.getPage(MobileMeasurements.Fields.measureMoment));
  }

  public MobileMeasurements post(PostMobileMeasurementRequest request) {
    Optional<Campaigns> campaign = campaignDao.findById(request.getCampaignId());

    return measurementDao.save(request.toEntity(campaign));
  }

}
