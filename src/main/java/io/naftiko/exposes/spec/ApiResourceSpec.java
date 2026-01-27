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
package io.naftiko.exposes.spec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.naftiko.spec.ResourceSpec;

/**
 * API Resource Specification Element
 */
public class ApiResourceSpec extends ResourceSpec {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private volatile List<ApiOperationSpec> operations;

    private volatile ApiForwardSpec forward;

    public ApiResourceSpec() {
        this(null, null, null, null, null);
    }
    
    public ApiResourceSpec(String path) {
        this(path, null, null, null, null);
    }

    public ApiResourceSpec(String path, String name, String label, String description, ApiForwardSpec forward) {
        super(path, name, label, description);
        this.operations = new CopyOnWriteArrayList<>();
        this.forward = forward;
    }

    public List<ApiOperationSpec> getOperations() {
        return operations;
    }

    public ApiForwardSpec getForward() {
        return forward;
    }

    public void setForward(ApiForwardSpec forward) {
        this.forward = forward;
    }

}
