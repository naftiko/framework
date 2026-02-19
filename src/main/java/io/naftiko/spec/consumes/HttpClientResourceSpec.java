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
package io.naftiko.spec.consumes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.naftiko.spec.ResourceSpec;

/**
 * HTTP Resource Specification Element
 */
public class HttpClientResourceSpec extends ResourceSpec {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private volatile List<HttpClientOperationSpec> operations;

    public HttpClientResourceSpec() {
        this(null, null, null, null);
    }

    public HttpClientResourceSpec(String path, String name, String label) {
        this(path, name, label, null);
    }

    public HttpClientResourceSpec(String path, String name, String label, String description) {
        super(path, name, label, description);
        this.operations = new CopyOnWriteArrayList<>();
    }   

    public List<HttpClientOperationSpec> getOperations() {
        return operations;
    }

    /**
     * Sets operations and establishes parent resource reference for each operation.
     * This ensures that each OperationSpec knows its parent ResourceSpec.
     * 
     * @JsonSetter ensures this method is called by Jackson during deserialization
     */
    @JsonSetter
    public void setOperations(List<HttpClientOperationSpec> operations) {
        this.operations = operations;

        if (operations != null) {
            for (HttpClientOperationSpec operation : operations) {
                operation.setParentResource(this);
            }
        }
    }
}
