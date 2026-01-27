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
package io.naftiko.consumes.spec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.naftiko.spec.OperationSpec;
import io.naftiko.spec.ResourceSpec;

/**
 * HTTP Resource Specification Element
 */
public class HttpResourceSpec extends ResourceSpec {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private volatile List<OperationSpec> operations;

    public HttpResourceSpec() {
        this(null, null, null, null);
    }

    public HttpResourceSpec(String path, String name, String label) {
        this(path, name, label, null);
    }

    public HttpResourceSpec(String path, String name, String label, String description) {
        super(path, name, label, description);
        this.operations = new CopyOnWriteArrayList<>();
    }   

    public List<OperationSpec> getOperations() {
        return operations;
    }

}
