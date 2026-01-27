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
package io.naftiko;

import java.io.File;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.naftiko.adapter.http.HttpAdapter;
import io.naftiko.adapter.proxy.ProxyAdapter;
import io.naftiko.config.NaftikoConfig;

/**
 * Main Capability class that initializes and manages adapters based on configuration
 */
public class Capability {

    private volatile NaftikoConfig config;
    private volatile ProxyAdapter proxyAdapter;
    private volatile HttpAdapter httpAdapter;

    public Capability(NaftikoConfig config) {
        this.config = config;

        // Initialize source adapters first
        this.httpAdapter = new HttpAdapter(this);

        // Then initialize sink adapters with reference to source adapters
        if(config.getCapability().getExposes().isEmpty()) {
            throw new IllegalArgumentException("Capability must expose at least one endpoint.");
        }
        
        this.proxyAdapter = new ProxyAdapter(this, config.getCapability().getExposes().get(0));
    }

    public NaftikoConfig getConfig() {
        return config;
    }

    public void setConfig(NaftikoConfig config) {
        this.config = config;
    }

    public ProxyAdapter getProxyAdapter() {
        return proxyAdapter;
    }

    public void setProxyAdapter(ProxyAdapter restAdapter) {
        this.proxyAdapter = restAdapter;
    }

    public HttpAdapter getHttpAdapter() {
        return httpAdapter;
    }

    public void setHttpAdapter(HttpAdapter httpAdapter) {
        this.httpAdapter = httpAdapter;
    }

    public void start() throws Exception {
        getProxyAdapter().start();
        getHttpAdapter().start();
    }

    public void stop() throws Exception {
        getProxyAdapter().stop();
        getHttpAdapter().stop();
    }

    /**
     * Luanch the capability, reading its configuration from local NAFTIKO.yaml file unless a
     * specific name is provided.
     * 
     * @param args The optional part and name of the capability configuration file.
     */
    public static void main(String[] args) {
        // Determine file path: Argument if provided, otherwise default
        String filePath = (args.length > 0) ? args[0] : "naftiko.yaml";

        File file = new File(filePath);
        System.out.println("Reading configuration from: " + file.getAbsolutePath());

        // Read the configuraton file
        if (file.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                NaftikoConfig config = mapper.readValue(file, NaftikoConfig.class);
                Capability capability = new Capability(config);
                capability.start();
                System.out.println("Capability started successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error reading file: " + e.getMessage());
            }
        } else {
            System.err.println("Error: File not found at " + filePath);
            System.exit(1);
        }
    }

}
