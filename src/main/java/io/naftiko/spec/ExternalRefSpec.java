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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base External Reference Specification Element.
 * Supports both file-resolved and runtime-resolved references for variable injection.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileResolvedExternalRefSpec.class, name = "environment"),
    @JsonSubTypes.Type(value = RuntimeResolvedExternalRefSpec.class, name = "variables")
})
public abstract class ExternalRefSpec {

    protected volatile String name;

    protected volatile String type;

    protected volatile String resolution;

    protected volatile ExternalRefKeysSpec keys;

    public ExternalRefSpec() {
        this(null, null, null, null);
    }

    public ExternalRefSpec(String name, String type, String resolution, ExternalRefKeysSpec keys) {
        this.name = name;
        this.type = type;
        this.resolution = resolution;
        this.keys = keys;
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

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public ExternalRefKeysSpec getKeys() {
        return keys;
    }

    public void setKeys(ExternalRefKeysSpec keys) {
        this.keys = keys;
    }

}
