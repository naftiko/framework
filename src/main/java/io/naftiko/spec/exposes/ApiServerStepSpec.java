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
package io.naftiko.spec.exposes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API Operation Step Specification Element
 * 
 * Represents a step in an API operation workflow.
 * A step contains a call specification that defines which operation to invoke
 * and what parameters to pass to it.
 */
public class ApiServerStepSpec {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile ApiServerCallSpec call;

    public ApiServerStepSpec() {
        this(null);
    }

    public ApiServerStepSpec(ApiServerCallSpec call) {
        this.call = call;
    }

    public ApiServerCallSpec getCall() {
        return call;
    }

    public void setCall(ApiServerCallSpec call) {
        this.call = call;
    }

}

