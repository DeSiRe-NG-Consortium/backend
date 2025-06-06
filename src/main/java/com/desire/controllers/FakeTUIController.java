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

package com.desire.controllers;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.desire.constants.Endpoints;
import com.desire.constants.Endpoints.TUI;
import com.desire.dtos.PostAGVMeasurementRequest.PostAGVMeasurementValueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Fake TUI controller to test the integration since we have no network connection to their backend
 * locally
 */
@Tag(name = TUI.TAG)
@Deprecated
@Validated
@RestController
@RequestMapping("/tui")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FakeTUIController {

  @Operation(summary = "Used when a campaign gets created in source system (NURO)")
  @PostMapping(Endpoints.TUI.CAMPAIGN)
  public ResponseEntity<?> postCampaign(@RequestBody String body) throws InterruptedException {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "Used when a campaign gets updated in source system (NURO)")
  @PatchMapping(Endpoints.TUI.CAMPAIGN)
  public ResponseEntity<?> patchCampaign(@RequestBody String body) throws InterruptedException {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "Used when AGV position gets created in source system (NURO)")
  @PostMapping(Endpoints.TUI.AGV_POSITIONS)
  public ResponseEntity<?> postAgvPositions(@RequestBody String body) throws InterruptedException {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "Fake endpoint where to pull data from TUI")
  @GetMapping(Endpoints.TUI.CAMPAIGN_RESULTS)
  public List<PostAGVMeasurementValueRequest> getCampaignResults() throws InterruptedException {
    return new ArrayList<>();
  }

}
