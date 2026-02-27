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
package io.naftiko.engine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.naftiko.Capability;
import io.naftiko.engine.exposes.ApiResourceRestlet;
import io.naftiko.engine.exposes.ApiServerAdapter;
import io.naftiko.spec.NaftikoSpec;
import io.naftiko.spec.exposes.ApiServerCallSpec;
import io.naftiko.spec.exposes.ApiServerOperationSpec;
import io.naftiko.spec.exposes.ApiServerResourceSpec;
import io.naftiko.spec.exposes.ApiServerSpec;
import org.restlet.Request;
import org.restlet.data.Method;
import org.restlet.data.MediaType;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

public class CapabilityHttpBodyIntegrationTest {

    private Capability capability;
    private ApiServerSpec serverSpec;
    private ApiServerResourceSpec resourceSpec;

    @BeforeEach
    public void setUp() throws Exception {
        String resourcePath = "src/test/resources/http-body-capability.yaml";
        File file = new File(resourcePath);
        assertTrue(file.exists(), "HTTP body capability file should exist at " + resourcePath);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        NaftikoSpec spec =
                mapper.readValue(file, NaftikoSpec.class);

        capability = new Capability(spec);

        // locate ApiResourceSpec
        ApiServerAdapter adapter = (ApiServerAdapter) capability.getServerAdapters().get(0);
        serverSpec = (ApiServerSpec) adapter.getSpec();
        resourceSpec = serverSpec.getResources().get(0);
    }

    @Test
    public void testClientRequestBodyTemplating() throws Exception {
        ApiServerOperationSpec serverOp = resourceSpec.getOperations().get(0);

        // Build a fake incoming request with JSON body
        String incomingJson = "{\"user\":{\"id\":\"123\",\"name\":\"Alice\"}}";
        Request req = new Request(Method.POST, "/users");
        req.setEntity(incomingJson, MediaType.APPLICATION_JSON);

        // Create restlet instance
        ApiResourceRestlet restlet =
                new ApiResourceRestlet(capability, serverSpec, resourceSpec);

        // Use reflection to call private prepareInputParameters and findClientRequestFor
        java.lang.reflect.Method buildMethod = ApiResourceRestlet.class
                .getDeclaredMethod("resolveInputParametersFromRequest", Request.class, ApiServerOperationSpec.class);
        buildMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> params =
                (Map<String, Object>) buildMethod.invoke(restlet, req, serverOp);

        assertNotNull(params, "Params map should be built");
        assertEquals("123", params.get("userId").toString(),
                "userId should be extracted from body");
        assertEquals("Alice", params.get("userName").toString(),
                "userName should be extracted from body");

        // Now call findClientRequestFor(ApiCallSpec, Map) reflectively to get HandlingContext
        java.lang.reflect.Method findMethod = ApiResourceRestlet.class.getDeclaredMethod(
                "findClientRequestFor", ApiServerCallSpec.class, Map.class);
        findMethod.setAccessible(true);
        Object handlingCtx = findMethod.invoke(restlet, serverOp.getCall(), params);

        // HandlingContext is package-private inner class; inspect its clientRequest entity via
        // reflection
        assertNotNull(handlingCtx, "HandlingContext should not be null");
        Field clientRequestField =
                handlingCtx.getClass().getDeclaredField("clientRequest");
        clientRequestField.setAccessible(true);
        Request clientRequest = (Request) clientRequestField.get(handlingCtx);

        assertNotNull(clientRequest, "Client Request should be constructed");
        assertNotNull(clientRequest.getEntity(), "Client request entity should be set");

        String body = clientRequest.getEntity().getText();
        assertTrue(body.contains("\"id\":\"123\""), "Templated body should contain id 123");
        assertTrue(body.contains("\"name\":\"Alice\""), "Templated body should contain name Alice");
    }
}
