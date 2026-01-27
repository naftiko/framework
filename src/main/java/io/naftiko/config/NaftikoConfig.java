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
package io.naftiko.config;

import java.util.List;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Naftiko Configuration Root, including version and capabilities
 */
public class NaftikoConfig {

    private volatile String naftiko;

    private volatile InfoConfig info;

    private volatile CapabilityConfig capability;

    public NaftikoConfig(String naftiko, InfoConfig info, CapabilityConfig capability) {
        this.naftiko = naftiko;
        this.info = info;
        this.capability = capability;
    }

    public NaftikoConfig() {
        this(null, null, null);
    }

    public String getNaftiko() {
        return naftiko;
    }

    public void setNaftiko(String naftiko) {
        this.naftiko = naftiko;
    }

    public InfoConfig getInfo() {
        return info;
    }

    public void setInfo(InfoConfig info) {
        this.info = info;
    }

    public CapabilityConfig getCapability() {
        return capability;
    }

    public void setCapability(CapabilityConfig capability) {
        this.capability = capability;
    }

    public static void main(String[] args) {
        try {
            // Serialize to JSON
            NaftikoConfig config = new NaftikoConfig();
            config.setNaftiko("0.2");
            config.setInfo(
                    new InfoConfig("Sample Capability", "A sample capability configuration"));
            config.setCapability(new CapabilityConfig());
            List<ExposesConfig> exposesConfigs = config.getCapability().getExposes();

            ExposesConfig exposes = new ExposesConfig("localhost", 8080);
            ForwardConfig forwardConfig = new ForwardConfig();
            forwardConfig.getTrustedHeaders().add("X-Notion");
            exposes.setForward(forwardConfig);
            exposesConfigs.add(exposes);

            List<ConsumesConfig> consumesConfigs = config.getCapability().getConsumes();
            ConsumesConfig consumes =
                    new ConsumesConfig("notion", "https://api.notion.com/v1/{{path}}", null);
            consumesConfigs.add(consumes);

            BasicAuthenticationConfig basicAuth =
                    new BasicAuthenticationConfig("scott", "tiger".toCharArray());
            consumes.setAuthentication(basicAuth);

            consumes = new ConsumesConfig("github", "https://api.github.com/v1/{{path}}", null);
            consumesConfigs.add(consumes);

            YAMLFactory yamlFactory = new YAMLFactory();
            ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
            String output = objectMapper.writeValueAsString(config);
            System.out.println(output);
        } catch (JacksonException e) {
            e.printStackTrace();
        }

    }

}
