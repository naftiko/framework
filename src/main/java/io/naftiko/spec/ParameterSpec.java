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

/**
 * Parameter Specification Element
 */
public class ParameterSpec extends StructureSpec {

    private volatile String in;

    private volatile String jsonPath;

    public ParameterSpec() {
        super();
    }

    public ParameterSpec(String name, String type, String in, String jsonPath) {
        super(name, type, null, null, null, null, null, null, null, null, null, null, null);
        this.in = in;
        this.jsonPath = jsonPath;
    }

    public ParameterSpec(String name, String type, StructureSpec items, StructureSpec values,
            String constant, String selector, String maxLength, Integer precision, Integer scale,
            String contentEncoding, String contentCompression, String contentMediaType,
            String description, String in, String jsonPath) {
        super(name, type, items, values, constant, selector, maxLength, precision, scale,
                contentEncoding, contentCompression, contentMediaType, description);
        this.in = in;
        this.jsonPath = jsonPath;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

}
