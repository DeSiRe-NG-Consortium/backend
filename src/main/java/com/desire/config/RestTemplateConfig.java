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

package com.desire.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for setting up the RestTemplate bean.
 */
@Configuration
public class RestTemplateConfig {

  public static final String VALUE_SHORT_TIMEOUT = "shortTimeout";

  // Timeout duration for the RestTemplate requests
  private final Duration defaultTimeout = Duration.ofSeconds(30);

  private final Duration shortTimeout = Duration.ofSeconds(5);

  /**
   * Creates and configures a RestTemplate bean with specified timeouts.
   *
   * @return a configured RestTemplate instance
   */
  @Bean(value = "default")
  @Primary
  public RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory();
    requestFactory.setConnectTimeout(defaultTimeout);
    requestFactory.setReadTimeout(defaultTimeout);

    return new RestTemplate(requestFactory);
  }

  @Bean(value = VALUE_SHORT_TIMEOUT)
  public RestTemplate restTemplateShortTimeout() {
    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory();
    requestFactory.setConnectTimeout(shortTimeout);
    requestFactory.setReadTimeout(shortTimeout);

    return new RestTemplate(requestFactory);
  }
}
