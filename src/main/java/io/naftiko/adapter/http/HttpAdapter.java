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
package io.naftiko.adapter.http;

import org.restlet.Client;
import io.naftiko.Adapter;
import io.naftiko.Capability;
import io.naftiko.config.ConsumesConfig;
import static org.restlet.data.Protocol.HTTP;
import static org.restlet.data.Protocol.HTTPS;
import java.util.List;

/**
 * HTTP Adapter implementation
 */
public class HttpAdapter extends Adapter {

    private volatile Capability capability;

    private volatile Client httpClient;

    public HttpAdapter(Capability capability) {
        this.capability = capability;
        this.httpClient = new Client(HTTP, HTTPS);
    }

    public Capability getCapability() {
        return capability;
    }

    public void setCapability(Capability capability) {
        this.capability = capability;
    }

    public List<ConsumesConfig> getConsumesConfig() {
        return getCapability().getConfig().getCapability().getConsumes();
    }

    public Client getHttpClient() {
        return httpClient;
    }

    public void init(ConsumesConfig configs) {
        
    }

    @Override
    public void start() throws Exception {
        getHttpClient().start();
    }

    @Override
    public void stop() throws Exception {
        getHttpClient().stop();
    }

}
