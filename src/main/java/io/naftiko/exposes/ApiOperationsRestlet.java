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
package io.naftiko.exposes;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import io.naftiko.Capability;
import io.naftiko.consumes.HttpClientAdapter;
import io.naftiko.exposes.spec.ApiForwardSpec;
import io.naftiko.exposes.spec.ApiOperationSpec;
import io.naftiko.exposes.spec.ApiResourceSpec;
import io.naftiko.exposes.spec.ApiStepSpec;
import io.naftiko.spec.OperationSpec;
import io.naftiko.spec.ResourceSpec;

/**
 * Restlet that handles structured API operations.
 */
public class ApiOperationsRestlet extends Restlet {

    private final Capability capability;
    private final ApiResourceSpec resourceSpec;

    public ApiOperationsRestlet(Capability capability, ApiResourceSpec resourceSpec) {
        this.capability = capability;
        this.resourceSpec = resourceSpec;
    }

    @Override
    public void handle(Request request, Response response) {
        HttpClientAdapter clientAdapter = null;
        Request clientRequest = null;

        // Find the matching operation based on the HTTP method
        for (ApiOperationSpec serverOp : getResourceSpec().getOperations()) {
            if (serverOp.getMethod().equals(request.getMethod().getName())) {
                for (ApiStepSpec step : serverOp.getSteps()) {
                    String clientCall = step.getCall();

                    if (clientCall != null) {
                        String[] tokens = clientCall.split("\\.");

                        if (tokens.length == 2) {
                            String clientNamespace = tokens[0];
                            String clientOpName = tokens[1];

                            for (HttpClientAdapter adapter : getCapability()
                                    .getHttpClientAdapters()) {
                                if (adapter.getSpec().getNamespace().equals(clientNamespace)) {
                                    OperationSpec clientOp =
                                            adapter.getOperationSpec(clientOpName);

                                    if (clientOp != null) {
                                        ResourceSpec clientRes = clientOp.getParentResource();
                                        String clientResUri = adapter.getSpec().getBaseUri()
                                                + clientRes.getPath();
                                        clientRequest = new Request(
                                                Method.valueOf(clientOp.getMethod()), clientResUri);
                                        clientAdapter = adapter;
                                    }
                                }
                            }
                        } else {
                            // Handle invalid call format
                            response.setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST);
                            response.setEntity("Invalid call format: " + clientCall,
                                    MediaType.TEXT_PLAIN);
                        }

                        return;
                    }
                }
            }
        }

        // If no client request was prepared from the operations, check if we have a forward
        // configuration
        ApiForwardSpec forward = getResourceSpec().getForward();

        if (clientRequest == null && forward != null) {
            for (HttpClientAdapter adapter : getCapability().getHttpClientAdapters()) {
                if (adapter.getSpec().getNamespace().equals(forward.getTargetNamespace())) {
                    // Prepare the HTTP client request
                    String path = (String) request.getAttributes().get("path");
                    String targetRef = adapter.getSpec().getBaseUri() + path;
                    clientRequest = new Request(request.getMethod(), targetRef);
                    clientRequest.setEntity(request.getEntity());
                    clientAdapter = adapter;

                    // Copy trusted headers from the original request to the client request
                    for (String trustedHeader : getResourceSpec().getForward()
                            .getTrustedHeaders()) {
                        String headerValue =
                                request.getHeaders().getFirstValue(trustedHeader, true);

                        if (headerValue != null) {
                            clientRequest.getHeaders().add(trustedHeader, headerValue);
                        }
                    }
                }
            }
        }

        // If we have a client request prepared, set the appropriate authentication and send it to
        // the target endpoint
        if (clientRequest != null) {
            Response clientResponse = new Response(clientRequest);
            clientAdapter.setChallengeResponse(clientRequest,
                    clientRequest.getResourceRef().toString());
            clientAdapter.setHeaders(clientRequest);

            // Send the request to the target endpoint
            clientAdapter.getHttpClient().handle(clientRequest, clientResponse);
            response.setStatus(clientResponse.getStatus());
            response.setEntity(clientResponse.getEntity());
            response.commit();
        } else {
            response.setStatus(org.restlet.data.Status.CLIENT_ERROR_NOT_FOUND);
            response.setEntity("Invalid call format", MediaType.TEXT_PLAIN);
        }
    }

    public Capability getCapability() {
        return capability;
    }

    public ApiResourceSpec getResourceSpec() {
        return resourceSpec;
    }

}
