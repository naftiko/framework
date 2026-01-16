/**
 * Copyright 2025-2026 Naftiko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.naftiko.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration of consumed adapter endpoints
 */
public class ConsumesConfig {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String expositionSuffix;

    private volatile String targetUri;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile AuthConfig authent;

    public ConsumesConfig(String expositionSuffix, String targetUri, AuthConfig authent) {
        this.expositionSuffix = expositionSuffix;
        this.targetUri = targetUri;
        this.authent = authent;
    }

    public ConsumesConfig(String targetUri) {
        this(null, targetUri, null);
    }

    public ConsumesConfig() {
        this(null);
    }

    public String getExpositionSuffix() {
        return expositionSuffix;
    }

    public void setExpositionSuffix(String expositionSuffix) {
        this.expositionSuffix = expositionSuffix;
    }

    public String getTargetUri() {
        return targetUri;
    }

    public void setTargetUri(String targetUri) {
        this.targetUri = targetUri;
    }

    public AuthConfig getAuthent() {
        return authent;
    }

    public void setAuthent(AuthConfig authent) {
        this.authent = authent;
    }

}
