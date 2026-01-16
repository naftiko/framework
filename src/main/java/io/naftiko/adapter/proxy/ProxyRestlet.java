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
package io.naftiko.adapter.proxy;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import io.naftiko.adapter.http.HttpAdapter;
import io.naftiko.config.ApiKeyAuthConfig;
import io.naftiko.config.AuthConfig;
import io.naftiko.config.BasicAuthConfig;
import io.naftiko.config.BearerAuthConfig;
import io.naftiko.config.ConsumesConfig;
import io.naftiko.config.DigestAuthConfig;

/**
 * Proxy Restlet to handle incoming requests and forward them to target endpoints
 */
public class ProxyRestlet extends Restlet {

    private final ProxyAdapter proxyAdapter;
    private final ConsumesConfig consumesConfig;

    public ProxyRestlet(ProxyAdapter proxyAdapter, ConsumesConfig consumesConfig) {
        this.proxyAdapter = proxyAdapter;
        this.consumesConfig = consumesConfig;
    }

    @Override
    public void handle(Request request, Response response) {
        // Prepare the proxy request
        String path = (String) request.getAttributes().get("path");
        String targetRef = consumesConfig.getTargetUri().replace("{{path}}", path);
        Request clientRequest = new Request(request.getMethod(), targetRef);
        Response clientResponse = new Response(clientRequest);
        clientRequest.setEntity(request.getEntity());

        AuthConfig authConfig = consumesConfig.getAuthent();

        if (authConfig != null) {
            // Add authentication headers if needed
            String type = authConfig.getType();
            ChallengeResponse challengeResponse = null;

            switch (type) {
                case "basic":
                    BasicAuthConfig basicAuth = (BasicAuthConfig) authConfig;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_BASIC);
                    challengeResponse.setIdentifier(basicAuth.getUsername());
                    challengeResponse.setSecret(basicAuth.getPassword());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "digest":
                    DigestAuthConfig digestAuth = (DigestAuthConfig) authConfig;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_DIGEST);
                    challengeResponse.setIdentifier(digestAuth.getUsername());
                    challengeResponse.setSecret(digestAuth.getPassword());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "bearer":
                    BearerAuthConfig bearerAuth = (BearerAuthConfig) authConfig;
                    challengeResponse =
                            new ChallengeResponse(ChallengeScheme.HTTP_OAUTH_BEARER);
                    challengeResponse.setRawValue(bearerAuth.getToken());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "apikey":
                    ApiKeyAuthConfig apiKeyAuth = (ApiKeyAuthConfig) authConfig;
                    String key = apiKeyAuth.getKey();
                    String value = apiKeyAuth.getValue();
                    String placement = apiKeyAuth.getPlacement();

                    if (placement.equals("header")) {
                        clientRequest.getHeaders().add(key, value);
                    } else if (placement.equals("query")) {
                        String separator = targetRef.contains("?") ? "&" : "?";
                        String newTargetRef = targetRef + separator + key + "=" + value;
                        clientRequest.setResourceRef(newTargetRef);
                    }
                    break;

                default:
                    break;
            }
        }

        // Forward the request to the target endpoint
        getHttpAdapter().getHttpClient().handle(clientRequest, clientResponse);
        response.setStatus(clientResponse.getStatus());
        response.setEntity(clientResponse.getEntity());
    }

    @Override
    public synchronized void start() throws Exception {
        super.start();
    }

    @Override
    public synchronized void stop() throws Exception {
        super.stop();
    }


    public ProxyAdapter getProxyAdapter() {
        return proxyAdapter;
    }

    public HttpAdapter getHttpAdapter() {
        return proxyAdapter.getCapability().getHttpAdapter();
    }

    public ConsumesConfig getConsumesConfig() {
        return consumesConfig;
    }

}
