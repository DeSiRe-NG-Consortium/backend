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

package com.desire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import de.codecentric.boot.admin.server.config.EnableAdminServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableScheduling
@EnableAsync
@EnableAdminServer
@EnableWebSecurity
@SpringBootApplication
public class DesireNGApp {

  public static void main(String[] args) {
    try {
      SpringApplication.run(DesireNGApp.class, args);
    } catch (Throwable e) {
      // due to bug in devtools:
      // https://stackoverflow.com/questions/32770884/breakpoint-at-throw-new-silentexitexception-in-eclipse-spring-boot
      if (e.getClass().getName().contains("SilentExitException")) {
        log.debug("Spring is restarting the main thread - See spring-boot-devtools");
      } else {
        log.error("Application crashed!", e);
      }
    }
  }

}
