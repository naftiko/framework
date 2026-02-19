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
package io.naftiko.engine.consumes;

import io.naftiko.Capability;
import io.naftiko.engine.Adapter;
import io.naftiko.spec.consumes.ClientSpec;
import io.naftiko.spec.consumes.HttpClientSpec;

/**
 * Client Adapter implementation
 */
public abstract class ClientAdapter extends Adapter {

    private volatile Capability capability;

    private volatile ClientSpec spec;

    public ClientAdapter(Capability capability, ClientSpec spec) {
        this.capability = capability;
        this.spec = spec;
    }

    public Capability getCapability() {
        return capability;
    }

    public void setCapability(Capability capability) {
        this.capability = capability;
    }

    public ClientSpec getSpec() {
        return spec;
    }

    public void setSpec(HttpClientSpec spec) {
        this.spec = spec;
    }

}
