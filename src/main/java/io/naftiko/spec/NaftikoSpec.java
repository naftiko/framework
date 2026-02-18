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
 * Naftiko Specification Root, including version and capabilities
 */
public class NaftikoSpec {

    private volatile String naftiko;

    private volatile InfoSpec info;

    private volatile CapabilitySpec capability;

    public NaftikoSpec(String naftiko, InfoSpec info, CapabilitySpec capability) {
        this.naftiko = naftiko;
        this.info = info;
        this.capability = capability;
    }

    public NaftikoSpec() {
        this(null, null, null);
    }

    public String getNaftiko() {
        return naftiko;
    }

    public void setNaftiko(String naftiko) {
        this.naftiko = naftiko;
    }

    public InfoSpec getInfo() {
        return info;
    }

    public void setInfo(InfoSpec info) {
        this.info = info;
    }

    public CapabilitySpec getCapability() {
        return capability;
    }

    public void setCapability(CapabilitySpec capability) {
        this.capability = capability;
    }   

}
