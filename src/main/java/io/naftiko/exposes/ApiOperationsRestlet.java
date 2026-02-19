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

        // Try to prepare a client request from configured operation steps
        ClientRequestContext ctx = prepareClientRequestFromOperationSteps(request, response);

        if (ctx.handled) {
            return;
        }

        clientAdapter = ctx.clientAdapter;
        clientRequest = ctx.clientRequest;

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
                    copyTrustedHeaders(request, clientRequest,
                            getResourceSpec().getForward().getTrustedHeaders());
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

    /**
     * Prepare a client request context by looking for a call configuration in the operation steps.
     * If a valid call configuration is found, constructs a client request context with the
     * corresponding client request and adapter. If an invalid call format is found, sets the
     * response to indicate a bad request and marks the context as handled.
     */
    private ClientRequestContext prepareClientRequestFromOperationSteps(Request request, Response response) {
        ClientRequestContext ctx = new ClientRequestContext();

        for (ApiOperationSpec serverOp : getResourceSpec().getOperations()) {
            if (serverOp.getMethod().equals(request.getMethod().getName())) {
                for (ApiStepSpec step : serverOp.getSteps()) {
                    String clientCall = step.getCall();

                    if (clientCall != null) {
                        String[] tokens = clientCall.split("\\.");

                        if (tokens.length == 2) {
                            String clientNamespace = tokens[0];
                            String clientOpName = tokens[1];
                            ClientRequestContext found = findClientRequestFor(clientNamespace, clientOpName);

                            if (found != null) {
                                ctx.clientRequest = found.clientRequest;
                                ctx.clientAdapter = found.clientAdapter;
                            }
                        } else {
                            // Handle invalid call format and mark as handled so caller returns
                            response.setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST);
                            response.setEntity("Invalid call format: " + clientCall,
                                    MediaType.TEXT_PLAIN);
                            ctx.handled = true;
                        }

                        return ctx;
                    }
                }
            }
        }

        return ctx;
    }

    /**
     * Find and construct a client request context for a given client namespace and operation name.
     * Returns null if no matching adapter/operation is found.
     */
    private ClientRequestContext findClientRequestFor(String clientNamespace, String clientOpName) {
        for (HttpClientAdapter adapter : getCapability().getHttpClientAdapters()) {
            if (adapter.getSpec().getNamespace().equals(clientNamespace)) {
                OperationSpec clientOp = adapter.getOperationSpec(clientOpName);

                if (clientOp != null) {
                    ResourceSpec clientRes = clientOp.getParentResource();
                    String clientResUri = adapter.getSpec().getBaseUri() + clientRes.getPath();
                    ClientRequestContext ctx = new ClientRequestContext();
                    ctx.clientRequest = new Request(Method.valueOf(clientOp.getMethod()), clientResUri);
                    ctx.clientAdapter = adapter;
                    return ctx;
                }
            }
        }

        return null;
    }

    /**
     * Copy a set of trusted headers from one request to another.
     */
    private void copyTrustedHeaders(Request from, Request to, Iterable<String> trustedHeaders) {
        if (trustedHeaders == null) {
            return;
        }

        for (String trustedHeader : trustedHeaders) {
            String headerValue = from.getHeaders().getFirstValue(trustedHeader, true);

            if (headerValue != null) {
                to.getHeaders().add(trustedHeader, headerValue);
            }
        }
    }

    private static class ClientRequestContext {
        Request clientRequest;
        HttpClientAdapter clientAdapter;
        boolean handled = false;
    }

}
