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

import io.naftiko.spec.OperationSpec;

/**
 * HTTP Operation Specification Element
 */
public class HttpClientOperationSpec extends OperationSpec {

    private volatile String body;

    public HttpClientOperationSpec() {
        this(null, null, null, null, null, null, null);
    }

    public HttpClientOperationSpec(HttpClientResourceSpec parentResource, String method, String name, String label) {
        this(parentResource, method, name, label, null, null, null);
    }

    public HttpClientOperationSpec(HttpClientResourceSpec parentResource, String method, String name, String label, String description, String body, String outputRawFormat) {
        this(parentResource, method, name, label, description, body, outputRawFormat, null);
    }

    public HttpClientOperationSpec(HttpClientResourceSpec parentResource, String method, String name, String label, String description, String body, String outputRawFormat, String outputSchema) {
        super(parentResource, method, name, label, description, outputRawFormat, outputSchema);
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

}
