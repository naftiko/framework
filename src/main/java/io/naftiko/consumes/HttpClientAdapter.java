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
package io.naftiko.consumes;

import org.restlet.Client;
import org.restlet.Request;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import io.naftiko.Adapter;
import io.naftiko.Capability;
import io.naftiko.consumes.spec.ApiKeyAuthenticationSpec;
import io.naftiko.consumes.spec.AuthenticationSpec;
import io.naftiko.consumes.spec.BasicAuthenticationSpec;
import io.naftiko.consumes.spec.BearerAuthenticationSpec;
import io.naftiko.consumes.spec.DigestAuthenticationSpec;
import io.naftiko.consumes.spec.HttpClientSpec;
import io.naftiko.consumes.spec.HttpResourceSpec;
import io.naftiko.spec.OperationSpec;
import io.naftiko.spec.ParameterSpec;
import static org.restlet.data.Protocol.HTTP;
import static org.restlet.data.Protocol.HTTPS;

/**
 * HTTP Client Adapter implementation
 */
public class HttpClientAdapter extends Adapter {

    private volatile Capability capability;

    private volatile HttpClientSpec spec;

    private final Client httpClient;

    public HttpClientAdapter(Capability capability, HttpClientSpec spec) {
        this.capability = capability;
        this.spec = spec;
        this.httpClient = new Client(HTTP, HTTPS);
    }

    /**
     * Finds the OperationSpec for a given operationId by searching through all resources and
     * their operations.
     * 
     * @param operationId The ID of the operation to find
     * @return The OperationSpec if found, or null if not found
     */
    public OperationSpec getOperationSpec(String operationId) {
        for (HttpResourceSpec res : getSpec().getResources()) {
            for (OperationSpec op : res.getOperations()) {
                if (op.getName().equals(operationId)) {
                    return op;
                }
            }
        }

        return null;
    }

    /**
     * Set any default headers from the input parameters on the client request
     */
    public void setHeaders(Request request) {
        // Set any default headers from the input parameters
        for (ParameterSpec param : getSpec().getInputParameters()) {
            if ("header".equals(param.getIn())) {
                request.getHeaders().set(param.getName(), param.getConstant());
            }
        }
    }

    /**
     * Set the appropriate authentication headers on the client request based on the specification
     */
    public void setChallengeResponse(Request request, String targetRef) {
        AuthenticationSpec authenticationSpec = getSpec().getAuthentication();

        if (authenticationSpec != null) {
            // Add authentication headers if needed
            String type = authenticationSpec.getType();
            ChallengeResponse challengeResponse = null;

            switch (type) {
                case "basic":
                    BasicAuthenticationSpec basicAuth =
                            (BasicAuthenticationSpec) authenticationSpec;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_BASIC);
                    challengeResponse.setIdentifier(basicAuth.getUsername());
                    challengeResponse.setSecret(basicAuth.getPassword());
                    request.setChallengeResponse(challengeResponse);
                    break;

                case "digest":
                    DigestAuthenticationSpec digestAuth =
                            (DigestAuthenticationSpec) authenticationSpec;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_DIGEST);
                    challengeResponse.setIdentifier(digestAuth.getUsername());
                    challengeResponse.setSecret(digestAuth.getPassword());
                    request.setChallengeResponse(challengeResponse);
                    break;

                case "bearer":
                    BearerAuthenticationSpec bearerAuth =
                            (BearerAuthenticationSpec) authenticationSpec;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_OAUTH_BEARER);
                    challengeResponse.setRawValue(bearerAuth.getToken());
                    request.setChallengeResponse(challengeResponse);
                    break;

                case "apikey":
                    ApiKeyAuthenticationSpec apiKeyAuth =
                            (ApiKeyAuthenticationSpec) authenticationSpec;
                    String key = apiKeyAuth.getKey();
                    String value = apiKeyAuth.getValue();
                    String placement = apiKeyAuth.getPlacement();

                    if (placement.equals("header")) {
                        request.getHeaders().add(key, value);
                    } else if (placement.equals("query")) {
                        String separator = targetRef.contains("?") ? "&" : "?";
                        String newTargetRef = targetRef + separator + key + "=" + value;
                        request.setResourceRef(newTargetRef);
                    }
                    break;

                default:
                    break;
            }
        }
    }

    public Capability getCapability() {
        return capability;
    }

    public void setCapability(Capability capability) {
        this.capability = capability;
    }

    public HttpClientSpec getSpec() {
        return spec;
    }

    public void setSpec(HttpClientSpec spec) {
        this.spec = spec;
    }

    public Client getHttpClient() {
        return httpClient;
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
