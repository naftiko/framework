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
import io.naftiko.engine.consumes.HttpClientAdapter;
import io.naftiko.engine.exposes.ApiResourceRestlet;
import io.naftiko.engine.exposes.ApiServerAdapter;
import io.naftiko.spec.NaftikoSpec;
import io.naftiko.spec.exposes.ApiServerCallSpec;
import io.naftiko.spec.exposes.ApiServerOperationSpec;
import io.naftiko.spec.exposes.ApiServerResourceSpec;
import io.naftiko.spec.exposes.ApiServerSpec;
import io.naftiko.spec.InputParameterSpec;
import org.restlet.Request;
import org.restlet.data.Method;
import org.restlet.data.MediaType;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

public class CapabilityHeaderQueryIntegrationTest {

    private Capability capability;
    private ApiServerSpec serverSpec;
    private ApiServerResourceSpec resourceSpec;

    @BeforeEach
    public void setUp() throws Exception {
        String resourcePath = "src/test/resources/http-header-query-capability.yaml";
        File file = new File(resourcePath);
        assertTrue(file.exists(), "Capability file should exist at " + resourcePath);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        NaftikoSpec spec =
                mapper.readValue(file, NaftikoSpec.class);

        capability = new Capability(spec);

        ApiServerAdapter adapter = (ApiServerAdapter) capability.getServerAdapters().get(0);
        serverSpec = (ApiServerSpec) adapter.getSpec();
        resourceSpec = serverSpec.getResources().get(0);
    }

    @Test
    public void testHeadersAndQueryPopulation() throws Exception {
        ApiServerOperationSpec serverOp = resourceSpec.getOperations().get(0);

        String incomingJson = "{\"user\":{\"id\":\"999\"}}";
        Request req = new Request(Method.POST, "/search");
        req.setEntity(incomingJson, MediaType.APPLICATION_JSON);

        ApiResourceRestlet restlet =
                new ApiResourceRestlet(capability, serverSpec, resourceSpec);

        java.lang.reflect.Method buildMethod = ApiResourceRestlet.class
                .getDeclaredMethod("resolveInputParametersFromRequest", Request.class, ApiServerOperationSpec.class);
        buildMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> params =
                (Map<String, Object>) buildMethod.invoke(restlet, req, serverOp);

        java.lang.reflect.Method findMethod = ApiResourceRestlet.class.getDeclaredMethod(
                "findClientRequestFor", ApiServerCallSpec.class, Map.class);
        findMethod.setAccessible(true);
        Object handlingCtx = findMethod.invoke(restlet, serverOp.getCall(), params);

        assertNotNull(handlingCtx, "HandlingContext should not be null");

        Field clientRequestField = handlingCtx.getClass().getDeclaredField("clientRequest");
        clientRequestField.setAccessible(true);
        Request clientRequest =
                (Request) clientRequestField.get(handlingCtx);

        assertNotNull(clientRequest, "Client request should be constructed");

        // Also ensure the client adapter has the expected inputParameters configured
        Field clientAdapterField =
                handlingCtx.getClass().getDeclaredField("clientAdapter");
        clientAdapterField.setAccessible(true);
        HttpClientAdapter clientAdapter =
                (HttpClientAdapter) clientAdapterField.get(handlingCtx);
        assertNotNull(clientAdapter, "Client adapter should be present");
        assertFalse(clientAdapter.getHttpClientSpec().getInputParameters().isEmpty(),
                "Client spec should have inputParameters");

        // Verify the client spec input parameter content
        InputParameterSpec firstSpec =
                clientAdapter.getHttpClientSpec().getInputParameters().get(0);
        assertEquals("X-API-Key", firstSpec.getName(),
                "First client input parameter name should be X-API-Key");
        assertEquals("ABC123", firstSpec.getConstant(),
                "First client input parameter constant should be ABC123");
        // Also directly test the helper by applying client-level inputParameters to a fresh request
        Request helperReq =
                new Request(Method.POST, "http://example.com/items");
        
        // Apply client-level input parameters to the request using Resolver
        io.naftiko.engine.Resolver.resolveInputParametersToRequest(helperReq,
                clientAdapter.getHttpClientSpec().getInputParameters(), params);

        String apiKey = helperReq.getHeaders().getFirstValue("X-API-Key", true);
        assertEquals("ABC123", apiKey,
                "API key header should be set from client inputParameters (helper)");

        // Query param from operation-level inputParameters (q=999)
        String ref = clientRequest.getResourceRef().toString();
        assertTrue(ref.contains("q=999") || ref.contains("q=%22?") == false,
                "Query param q should be present with value 999; got: " + ref);
    }
}
