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
import io.naftiko.config.ApiKeyAuthenticationConfig;
import io.naftiko.config.AuthenticationConfig;
import io.naftiko.config.BasicAuthenticationConfig;
import io.naftiko.config.BearerAuthenticationConfig;
import io.naftiko.config.ConsumesConfig;
import io.naftiko.config.DigestAuthenticationConfig;

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

        AuthenticationConfig authenticationConfig = consumesConfig.getAuthentication();

        if (authenticationConfig != null) {
            // Add authentication headers if needed
            String type = authenticationConfig.getType();
            ChallengeResponse challengeResponse = null;

            switch (type) {
                case "basic":
                    BasicAuthenticationConfig basicAuth = (BasicAuthenticationConfig) authenticationConfig;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_BASIC);
                    challengeResponse.setIdentifier(basicAuth.getUsername());
                    challengeResponse.setSecret(basicAuth.getPassword());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "digest":
                    DigestAuthenticationConfig digestAuth = (DigestAuthenticationConfig) authenticationConfig;
                    challengeResponse = new ChallengeResponse(ChallengeScheme.HTTP_DIGEST);
                    challengeResponse.setIdentifier(digestAuth.getUsername());
                    challengeResponse.setSecret(digestAuth.getPassword());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "bearer":
                    BearerAuthenticationConfig bearerAuth = (BearerAuthenticationConfig) authenticationConfig;
                    challengeResponse =
                            new ChallengeResponse(ChallengeScheme.HTTP_OAUTH_BEARER);
                    challengeResponse.setRawValue(bearerAuth.getToken());
                    clientRequest.setChallengeResponse(challengeResponse);
                    break;

                case "apikey":
                    ApiKeyAuthenticationConfig apiKeyAuth = (ApiKeyAuthenticationConfig) authenticationConfig;
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

        // Copy forward headers from the original request
        for (String header : proxyAdapter.getExposesConfig().getForwardHeaders()) {
            String headerValue = request.getHeaders().getFirstValue(header);
            
            if (headerValue != null) {
                clientRequest.getHeaders().add(header, headerValue);
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
