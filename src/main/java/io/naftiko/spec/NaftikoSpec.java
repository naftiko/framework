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

import java.util.List;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.naftiko.consumes.spec.BasicAuthenticationSpec;
import io.naftiko.consumes.spec.HttpClientSpec;
import io.naftiko.consumes.spec.HttpResourceSpec;
import io.naftiko.exposes.spec.ApiForwardSpec;
import io.naftiko.exposes.spec.ApiOperationSpec;
import io.naftiko.exposes.spec.ApiResourceSpec;
import io.naftiko.exposes.spec.ApiServerSpec;
import io.naftiko.exposes.spec.ApiStepSpec;
import io.naftiko.exposes.spec.ServerSpec;

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

    public static void main(String[] args) {
        try {
            // Serialize to JSON
            NaftikoSpec config = new NaftikoSpec();
            config.setNaftiko("0.3");
            config.setInfo(new InfoSpec("Sample Capability", "A sample capability configuration",
                    "2026-01-01", "2026-02-12"));
            config.setCapability(new CapabilitySpec());
            List<ServerSpec> exposesConfigs = config.getCapability().getExposes();

            ApiServerSpec apiServerConfig = new ApiServerSpec("localhost", 9090, "sample");
            exposesConfigs.add(apiServerConfig);

            ApiResourceSpec apiRes = new ApiResourceSpec("/users/{{username}}");
            ApiOperationSpec apiOp =
                    new ApiOperationSpec(apiRes, "GET", "get-user", "Get User");
            apiOp.getSteps().add(new ApiStepSpec("github.get-user"));
            apiRes.getOperations().add(apiOp);

            apiRes = new ApiResourceSpec("/databases/{{database_id}}");
            apiRes.getInputParameters().add(new ParameterSpec("database_id", "path"));
            apiOp = new ApiOperationSpec(apiRes, "GET", "get-db", "Get Database");
            apiOp.getOutputParameters().add(new ParameterSpec("database_id",
                    "$.consumes.notion.DB.get-database.outputParameters.{{dbId}}"));
            apiOp.getOutputParameters().add(new ParameterSpec("Api-Version", "v1"));
            apiOp.getOutputParameters().add(new ParameterSpec("name",
                    "$.consumes.notion.DB.get-database.outputParameters.{{dbName}}"));
            apiOp.getSteps().add(new ApiStepSpec("notion.get-database"));
            apiRes.getOperations().add(apiOp);
            apiServerConfig.getResources().add(apiRes);

            apiRes = new ApiResourceSpec("/notion/{{path}}");
            ApiForwardSpec forwardConfig = new ApiForwardSpec("notion");
            forwardConfig.getTrustedHeaders().add("Notion-Version");
            apiRes.setForward(forwardConfig);
            apiServerConfig.getResources().add(apiRes);

            apiRes = new ApiResourceSpec("/github/{{path}}");
            forwardConfig = new ApiForwardSpec("github");
            forwardConfig.getTrustedHeaders().add("Notion-Version");
            apiRes.setForward(forwardConfig);
            apiServerConfig.getResources().add(apiRes);

            // First HTTP Client Adapter for Notion
            List<HttpClientSpec> consumesConfigs = config.getCapability().getConsumes();
            HttpClientSpec httpClientConfig =
                    new HttpClientSpec("notion", "https://api.notion.com/v1/{{path}}", null);
            httpClientConfig.getInputParameters()
                    .add(new ParameterSpec("header", "Notion-Version", "2022-06-28"));

            BasicAuthenticationSpec basicAuth =
                    new BasicAuthenticationSpec("scott", "tiger".toCharArray());
            httpClientConfig.setAuthentication(basicAuth);

            HttpResourceSpec httpRes =
                    new HttpResourceSpec("databases/{{database_id}}", "db", "Database resource");
            httpRes.getInputParameters().add(new ParameterSpec("database_id", "1234", "path"));

            OperationSpec op =
                    new OperationSpec(httpRes, "GET", "get-database", "Get Database");
            op.getOutputParameters().add(new ParameterSpec("dbName", "$.title[0].text.content"));
            op.getOutputParameters().add(new ParameterSpec("dbId", "$.id"));
            httpRes.getOperations().add(op);

            op = new OperationSpec(httpRes, "PUT", "update-database", "Update Database");
            httpRes.getOperations().add(op);

            op = new OperationSpec(httpRes, "DELETE", "delete-database", "Delete Database");
            httpRes.getOperations().add(op);
            httpClientConfig.getResources().add(httpRes);

            httpRes = new HttpResourceSpec();
            httpRes.setPath("databases/{{database_id}}/query");
            op = new OperationSpec(httpRes, "POST", "query-database", "Query Database");
            httpRes.getOperations().add(op);
            httpClientConfig.getResources().add(httpRes);

            consumesConfigs.add(httpClientConfig);

            // Another HTTP Client Adapter for GitHub
            httpClientConfig =
                    new HttpClientSpec("github", "https://api.github.com/v1/{{path}}", null);
            consumesConfigs.add(httpClientConfig);

            httpRes = new HttpResourceSpec("users/{{username}}", "user", "User resource");
            op = new OperationSpec(httpRes, "GET", "get-user", "Get User" );
            httpRes.getOperations().add(op);
            httpClientConfig.getResources().add(httpRes);

            // Serialize to YAML
            YAMLFactory yamlFactory = new YAMLFactory();
            ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
            String output = objectMapper.writeValueAsString(config);
            System.out.println(output);
        } catch (JacksonException e) {
            e.printStackTrace();
        }

    }

}
