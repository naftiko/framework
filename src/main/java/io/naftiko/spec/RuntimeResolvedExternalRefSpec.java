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
 * Runtime-Resolved External Reference Specification Element.
 * Variables are injected at runtime by the execution environment (default).
 * The capability document does not specify where the values come from
 * — this is delegated to the deployment platform.
 */
public class RuntimeResolvedExternalRefSpec extends ExternalRefSpec {

    public RuntimeResolvedExternalRefSpec() {
        super("env-runtime", "variables", "runtime", null);
    }

    public RuntimeResolvedExternalRefSpec(String name, ExternalRefKeysSpec keys) {
        super(name, "variables", "runtime", keys);
    }

}
