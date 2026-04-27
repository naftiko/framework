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
package io.naftiko.spec.exposes.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.naftiko.spec.OutputParameterSpec;
import io.naftiko.spec.util.OperationStepSpec;
import io.naftiko.spec.exposes.ServerCallSpec;

/**
 * MCP Resource Specification Element.
 *
 * Defines an MCP resource that exposes data agents can read. Two source types are supported:
 * <ul>
 *   <li><b>Dynamic</b> ({@code call}/{@code steps}): backed by consumed HTTP operations — same
 *       orchestration model as tools.</li>
 *   <li><b>Static</b> ({@code location}): served from local files identified by a
 *       {@code file:///} URI.</li>
 * </ul>
 */
public class McpServerResourceSpec {

    private volatile String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String label;

    private volatile String uri;

    private volatile String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String mimeType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile ServerCallSpec call;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile Map<String, Object> with;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<OperationStepSpec> steps;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<OutputParameterSpec> outputParameters;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String location;

    public McpServerResourceSpec() {
        this.steps = new CopyOnWriteArrayList<>();
        this.outputParameters = new CopyOnWriteArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public ServerCallSpec getCall() {
        return call;
    }

    public void setCall(ServerCallSpec call) {
        this.call = call;
    }

    public Map<String, Object> getWith() {
        return with;
    }

    public void setWith(Map<String, Object> with) {
        this.with = with != null ? new ConcurrentHashMap<>(with) : null;
    }

    public List<OperationStepSpec> getSteps() {
        return steps;
    }

    public List<OutputParameterSpec> getOutputParameters() {
        return outputParameters;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Returns {@code true} when this resource is served from a local file directory.
     */
    public boolean isStatic() {
        return location != null;
    }

    /**
     * Returns {@code true} when the URI contains {@code {param}} placeholders (resource template).
     */
    public boolean isTemplate() {
        return uri != null && uri.contains("{") && uri.contains("}");
    }
}
