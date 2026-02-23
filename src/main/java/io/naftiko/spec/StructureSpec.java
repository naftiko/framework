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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON Structure Specification Element
 */
public class StructureSpec {

    @JsonProperty("name")
    private volatile String name;

    @JsonProperty("type")
    private volatile String type;

    @JsonProperty("properties")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<StructureSpec> properties;

    @JsonProperty("required")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<String> required;

    @JsonProperty("items")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile StructureSpec items;

    @JsonProperty("values")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private volatile StructureSpec values;

    @JsonProperty("choices")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<StructureSpec> choices;

    @JsonProperty("const")
    private volatile String constant;

    @JsonProperty("enum")
    private final List<String> enumeration;

    @JsonProperty("selector")
    private volatile String selector;

    @JsonProperty("tuple")
    private final List<String> tuple;

    @JsonProperty("maxLength")
    private volatile String maxLength;

    @JsonProperty("precision")
    private volatile Integer precision;

    @JsonProperty("scale")
    private volatile Integer scale;

    @JsonProperty("contentEncoding")
    private volatile String contentEncoding;

    @JsonProperty("contentCompression")
    private volatile String contentCompression;

    @JsonProperty("contentMediaType")
    private volatile String contentMediaType;

    @JsonProperty("description")
    private volatile String description;

    @JsonProperty("examples")
    private final List<String> examples;

    public StructureSpec() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public StructureSpec(String name, String type, StructureSpec items, StructureSpec values,
            String constant, String selector, String maxLength, Integer precision, Integer scale,
            String contentEncoding, String contentCompression, String contentMediaType,
            String description) {
        this.name = name;
        this.type = type;
        this.properties = new CopyOnWriteArrayList<>();
        this.required = new CopyOnWriteArrayList<>();
        this.items = items;
        this.values = values;
        this.constant = constant;
        this.enumeration = new CopyOnWriteArrayList<>();
        this.choices = new CopyOnWriteArrayList<>();
        this.selector = selector;
        this.tuple = new CopyOnWriteArrayList<>();
        this.maxLength = maxLength;
        this.precision = precision;
        this.scale = scale;
        this.contentEncoding = contentEncoding;
        this.contentCompression = contentCompression;
        this.contentMediaType = contentMediaType;
        this.description = description;
        this.examples = new CopyOnWriteArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<StructureSpec> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    public StructureSpec getItems() {
        return items;
    }

    public void setItems(StructureSpec items) {
        this.items = items;
    }

    public StructureSpec getValues() {
        return values;
    }

    public void setValues(StructureSpec values) {
        this.values = values;
    }

    public String getConstant() {
        return constant;
    }

    public void setConstant(String constant) {
        this.constant = constant;
    }

    public List<String> getEnumeration() {
        return this.enumeration;
    }

    public List<StructureSpec> getChoices() {
        return choices;
    }

}
