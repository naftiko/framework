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
public class ParameterSpec {

    private volatile String in;

    private volatile String name;

    private volatile String value;

    private volatile String defaultValue;  

    public ParameterSpec() {
        this(null, null, null, null);
    }

    public ParameterSpec(String in, String name) {
        this(name, null, in, null);
    }

    public ParameterSpec(String in, String name, String value) {
        this(in, name, value, null);
    }

    public ParameterSpec(String in, String name, String value, String defaultValue) {
        this.in = in;
        this.name = name;
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

}
