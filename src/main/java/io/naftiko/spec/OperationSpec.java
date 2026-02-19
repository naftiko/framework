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
package io.naftiko.spec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Operation Specification Element
 */
public class OperationSpec {

    @JsonIgnore
    private volatile ResourceSpec parentResource;

    private volatile String method;

    private volatile String name;

    private volatile String label;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<InputParameterSpec> inputParameters;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String outputRawFormat;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile String outputSchema;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<OutputParameterSpec> outputParameters;

    public OperationSpec() {
        this(null, null, null, null, null, null, null);
    }

    public OperationSpec(ResourceSpec parentResource, String method, String name, String label) {
        this(parentResource, method, name, label, null, null, null);
    }

    public OperationSpec(ResourceSpec parentResource, String method, String name, String label, String description, String outputRawFormat) {
        this(parentResource, method, name, label, description, outputRawFormat, null);
    }

    public OperationSpec(ResourceSpec parentResource, String method, String name, String label, String description, String outputRawFormat, String outputSchema) {
        this.parentResource = parentResource;
        this.method = method;
        this.name = name;
        this.label = label;
        this.description = description;
        this.outputRawFormat = outputRawFormat;
        this.outputSchema = outputSchema;
        this.inputParameters = new CopyOnWriteArrayList<>();
        this.outputParameters = new CopyOnWriteArrayList<>();
    }

    public ResourceSpec getParentResource() {
        return parentResource;
    }

    /**
     * Sets the parent resource for this operation.
     * This is called during deserialization to establish the parent-child relationship.
     * 
     * @param parentResource the parent ResourceSpec
     */
    public void setParentResource(ResourceSpec parentResource) {
        this.parentResource = parentResource;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<InputParameterSpec> getInputParameters() {
        return inputParameters;
    }

    public String getOutputRawFormat() {
        return outputRawFormat;
    }

    public void setOutputRawFormat(String outputRawFormat) {
        this.outputRawFormat = outputRawFormat;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }
    
    public List<OutputParameterSpec> getOutputParameters() {
        return outputParameters;
    }

}