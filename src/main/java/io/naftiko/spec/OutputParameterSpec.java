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
 * Output Parameter Specification Element
 */
public class OutputParameterSpec extends StructureSpec {

    private volatile String in;

    private volatile String valuePath;

    public OutputParameterSpec() {
        super();
    }

    public OutputParameterSpec(String name, String type, String in, String valuePath) {
        super(name, type, null, null, null, null, null, null, null, null, null, null, null);
        this.in = in;
        this.valuePath = valuePath;
    }

    public OutputParameterSpec(String name, String type, StructureSpec items, StructureSpec values,
            String constant, String selector, String maxLength, Integer precision, Integer scale,
            String contentEncoding, String contentCompression, String contentMediaType,
            String description, String in, String valuePath) {
        super(name, type, items, values, constant, selector, maxLength, precision, scale,
                contentEncoding, contentCompression, contentMediaType, description);
        this.in = in;
        this.valuePath = valuePath;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

}
