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
package io.naftiko.engine.exposes;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import io.naftiko.Capability;
import io.naftiko.engine.Converter;
import io.naftiko.engine.Resolver;
import io.naftiko.engine.consumes.ClientAdapter;
import io.naftiko.engine.consumes.HttpClientAdapter;
import io.naftiko.spec.InputParameterSpec;
import io.naftiko.spec.OutputParameterSpec;
import io.naftiko.spec.consumes.HttpClientOperationSpec;
import io.naftiko.spec.exposes.ApiServerCallSpec;
import io.naftiko.spec.exposes.ApiServerForwardSpec;
import io.naftiko.spec.exposes.ApiServerOperationSpec;
import io.naftiko.spec.exposes.ApiServerResourceSpec;
import io.naftiko.spec.exposes.ApiServerSpec;
import io.naftiko.spec.exposes.ApiServerStepSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restlet that handles structured API operations.
 */
public class ApiOperationsRestlet extends Restlet {

    private final Capability capability;
    private final ApiServerSpec serverSpec;
    private final ApiServerResourceSpec resourceSpec;

    public ApiOperationsRestlet(Capability capability, ApiServerSpec serverSpec,
            ApiServerResourceSpec resourceSpec) {
        this.capability = capability;
        this.serverSpec = serverSpec;
        this.resourceSpec = resourceSpec;
    }

    @Override
    public void handle(Request request, Response response) {
        boolean handled = handleFromOperationSpec(request, response);

        if (!handled && getResourceSpec().getForward() != null) {
            handled = handleFromForwardSpec(request, response);
        }

        if (!handled) {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            response.setEntity(
                    "Unable to handle the request. Please check the capability specification.",
                    MediaType.TEXT_PLAIN);
        }
    }

    /**
     * Prepare a client request context by looking for a call configuration in the operation steps.
     * If a valid call configuration is found, constructs a client request context with the
     * corresponding client request and adapter. If an invalid call format is found, sets the
     * response to indicate a bad request and marks the context as handled.
     */
    private boolean handleFromOperationSpec(Request request, Response response) {
        HandlingContext found = null;

        for (ApiServerOperationSpec serverOp : getResourceSpec().getOperations()) {

            if (serverOp.getMethod().equals(request.getMethod().getName())) {

                // Build request-scoped input parameter map (resource + operation)
                Map<String, Object> inputParameters =
                        resolveInputParametersFromRequest(request, serverOp);

                // Include operation-level 'with' parameters for template resolution
                if (serverOp.getWith() != null) {
                    inputParameters.putAll(serverOp.getWith());
                }

                if (serverOp.getCall() != null) {
                    try {
                        found = findClientRequestFor(serverOp.getCall(), inputParameters);
                    } catch (IllegalArgumentException e) {
                        response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                        response.setEntity("Error resolving request parameters: " + e.getMessage(),
                                MediaType.TEXT_PLAIN);
                        return true;
                    }

                    if (found != null) {
                        try {
                            // Send the request to the target endpoint
                            found.handle();
                            response.setStatus(found.clientResponse.getStatus());
                        } catch (Exception e) {
                            response.setStatus(Status.SERVER_ERROR_INTERNAL);
                            response.setEntity(
                                    "Error while handling an HTTP client call\n\n" + e.toString(),
                                    MediaType.TEXT_PLAIN);
                            return true;
                        }

                        sendResponse(serverOp, response, found);
                        return true;
                    } else {
                        response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                        response.setEntity("Invalid call format: "
                                + (serverOp.getCall() != null ? serverOp.getCall().getOperation()
                                        : "null"),
                                MediaType.TEXT_PLAIN);
                        return true;
                    }
                } else {
                    for (ApiServerStepSpec step : serverOp.getSteps()) {
                        // Merge step-level 'with' parameters if present
                        Map<String, Object> stepParams = new ConcurrentHashMap<>(inputParameters);
                        
                        // First merge step-level 'with' parameters
                        if (step.getWith() != null) {
                            stepParams.putAll(step.getWith());
                        }
                        
                        // Then merge call-level 'with' parameters (call level takes precedence)
                        if (step.getCall() != null && step.getCall().getWith() != null) {
                            stepParams.putAll(step.getCall().getWith());
                        }

                        try {
                            found = findClientRequestFor(step.getCall(), stepParams);
                        } catch (IllegalArgumentException e) {
                            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                            response.setEntity(
                                    "Error resolving request parameters: " + e.getMessage(),
                                    MediaType.TEXT_PLAIN);
                            return true;
                        }

                        if (found != null) {
                            try {
                                // Send the request to the target endpoint
                                found.handle();
                            } catch (Exception e) {
                                response.setStatus(Status.SERVER_ERROR_INTERNAL);
                                response.setEntity("Error while handling an HTTP client call\n\n"
                                        + e.toString(), MediaType.TEXT_PLAIN);
                                return true;
                            }
                        } else {
                            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                            response.setEntity("Invalid call format: "
                                    + (step.getCall() != null ? step.getCall().getOperation()
                                            : "null"),
                                    MediaType.TEXT_PLAIN);
                            return true;
                        }
                    }

                    if (found != null) {
                        // Return the response based on the last client request
                        response.setStatus(found.clientResponse.getStatus());
                        sendResponse(serverOp, response, found);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void sendResponse(ApiServerOperationSpec serverOp, Response response,
            HandlingContext found) {
        // Apply output mappings if present or forward the raw entity
        if (serverOp.getOutputParameters() != null && !serverOp.getOutputParameters().isEmpty()) {
            try {
                String mapped = mapOutputParameters(serverOp, found);

                if (mapped != null) {
                    response.setEntity(mapped, MediaType.APPLICATION_JSON);
                } else {
                    response.setEntity(found.clientResponse.getEntity());
                }
            } catch (Exception e) {
                response.setStatus(Status.SERVER_ERROR_INTERNAL);
                response.setEntity("Failed to map output parameters: " + e.getMessage(),
                        MediaType.TEXT_PLAIN);
            }
        } else {
            response.setEntity(found.clientResponse.getEntity());
        }

        response.commit();
    }

    /**
     * Prepare a client request context based on a forward specification. This is used for direct
     * calls to the resource that should be forwarded to a target endpoint without going through the
     * operation steps.
     */
    private boolean handleFromForwardSpec(Request request, Response response) {
        for (ClientAdapter adapter : getCapability().getClientAdapters()) {
            if (adapter instanceof HttpClientAdapter) {
                HttpClientAdapter httpAdapter = (HttpClientAdapter) adapter;
                ApiServerForwardSpec forwardSpec = getResourceSpec().getForward();

                if (httpAdapter.getHttpClientSpec().getNamespace()
                        .equals(forwardSpec.getTargetNamespace())) {
                    // Prepare the HTTP client request
                    String path = (String) request.getAttributes().get("path");
                    String targetRef = httpAdapter.getHttpClientSpec().getBaseUri() + path;
                    Request clientRequest = new Request(request.getMethod(), targetRef);
                    clientRequest.setEntity(request.getEntity());

                    // Copy trusted headers from the original request to the client request
                    copyTrustedHeaders(request, clientRequest,
                            getResourceSpec().getForward().getTrustedHeaders());

                    // Resolve HTTP client input parameters for authentication template resolution
                    Map<String, Object> parameters = new ConcurrentHashMap<>();
                    Resolver.resolveInputParametersToRequest(clientRequest,
                            httpAdapter.getHttpClientSpec().getInputParameters(), parameters);

                    // Set any authentication needed on the client request
                    Response clientResponse = new Response(clientRequest);
                    httpAdapter.setChallengeResponse(clientRequest,
                            clientRequest.getResourceRef().toString(), parameters);
                    httpAdapter.setHeaders(clientRequest);

                    // Send the request to the target endpoint
                    httpAdapter.getHttpClient().handle(clientRequest, clientResponse);
                    response.setStatus(clientResponse.getStatus());
                    response.setEntity(clientResponse.getEntity());
                    response.commit();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Variant that merges incoming request parameters with call-level 'with' values.
     */
    private HandlingContext findClientRequestFor(ApiServerCallSpec call,
            Map<String, Object> requestParams) {

        if (call == null) {
            return null;
        }

        Map<String, Object> merged = new HashMap<>();

        if (requestParams != null) {
            merged.putAll(requestParams);
        }

        if (call.getWith() != null) {
            merged.putAll(call.getWith());
        }

        if (call.getOperation() != null) {
            String[] tokens = call.getOperation().split("\\.");
            if (tokens.length == 2) {
                return findClientRequestFor(tokens[0], tokens[1], merged);
            }
        }

        return null;
    }

    /**
     * Find and construct a client request context for a given client namespace, operation name, and
     * optional parameters. Returns null if no matching adapter/operation is found.
     */
    private HandlingContext findClientRequestFor(String clientNamespace, String clientOpName,
            Map<String, Object> parameters) {

        for (ClientAdapter adapter : getCapability().getClientAdapters()) {
            if (adapter instanceof HttpClientAdapter) {
                HttpClientAdapter clientAdapter = (HttpClientAdapter) adapter;

                if (clientAdapter.getHttpClientSpec().getNamespace().equals(clientNamespace)) {
                    HttpClientOperationSpec clientOp = clientAdapter.getOperationSpec(clientOpName);

                    if (clientOp != null) {
                        String clientResUri = clientAdapter.getHttpClientSpec().getBaseUri()
                                + clientOp.getParentResource().getPath();

                        // Resolve any Mustache templates in the URI using parameters
                        clientResUri = Resolver.resolveMustacheTemplate(clientResUri, parameters);

                        // Validate that all templates have been resolved
                        if (clientResUri.contains("{{") && clientResUri.contains("}}")) {
                            throw new IllegalArgumentException(
                                    "Unresolved template parameters in URI: " + clientResUri
                                            + ". Available parameters: "
                                            + (parameters != null ? parameters.keySet() : "none"));
                        }

                        HandlingContext ctx = new HandlingContext();
                        ctx.clientRequest = new Request();
                        ctx.clientAdapter = clientAdapter;
                        ctx.clientResponse = new Response(ctx.clientRequest);

                        // Apply client-level and operation-level input parameters
                        Resolver.resolveInputParametersToRequest(ctx.clientRequest,
                                clientAdapter.getHttpClientSpec().getInputParameters(), parameters);
                        Resolver.resolveInputParametersToRequest(ctx.clientRequest,
                                clientOp.getInputParameters(), parameters);

                        ctx.clientRequest.setMethod(Method.valueOf(clientOp.getMethod()));
                        ctx.clientRequest.setResourceRef(new Reference(
                                Resolver.resolveMustacheTemplate(clientResUri, parameters)));

                        if (clientOp.getBody() != null) {
                            String resolvedBody = Resolver
                                    .resolveMustacheTemplate(clientOp.getBody(), parameters);

                            // Validate resolved body doesn't have unresolved templates
                            if (resolvedBody.contains("{{") && resolvedBody.contains("}}")) {
                                throw new IllegalArgumentException(
                                        "Unresolved template parameters in body: " + resolvedBody
                                                + ". Available parameters: "
                                                + (parameters != null ? parameters.keySet()
                                                        : "none"));
                            }

                            ctx.clientRequest.setEntity(resolvedBody, MediaType.APPLICATION_JSON);
                        }

                        // Set any authentication needed on the client request
                        ctx.clientAdapter.setChallengeResponse(ctx.clientRequest,
                                ctx.clientRequest.getResourceRef().toString(), parameters);
                        ctx.clientAdapter.setHeaders(ctx.clientRequest);
                        return ctx;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Build a map of input parameter values for a given request and operation by evaluating
     * resource-level and operation-level InputParameterSpec entries.
     */
    private Map<String, Object> resolveInputParametersFromRequest(Request request,
            ApiServerOperationSpec serverOp) {
        Map<String, Object> params = new ConcurrentHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tmpRoot = null;

        // Read request body once (may be null)
        try {
            if ((request.getEntity() != null) && !request.getEntity().isEmpty()) {
                tmpRoot = mapper.readTree(request.getEntity().getReader());
            }
        } catch (Exception e) {
            tmpRoot = null;
        }

        final JsonNode root = tmpRoot;

        // Server-level input parameters
        if (getServerSpec().getInputParameters() != null) {
            for (InputParameterSpec spec : getServerSpec().getInputParameters()) {
                Object val = Resolver.resolveInputParameterFromRequest(spec, request, root, mapper);

                if (val != null) {
                    params.put(spec.getName(), val);
                }
            }
        }

        // Resource-level input parameters
        if (getResourceSpec().getInputParameters() != null) {
            for (InputParameterSpec spec : getResourceSpec().getInputParameters()) {
                Object v = Resolver.resolveInputParameterFromRequest(spec, request, root, mapper);
                if (v != null) {
                    params.put(spec.getName(), v);
                }
            }
        }

        // Operation-level input parameters override resource-level
        if (serverOp.getInputParameters() != null) {
            for (InputParameterSpec spec : serverOp.getInputParameters()) {
                Object v = Resolver.resolveInputParameterFromRequest(spec, request, root, mapper);
                if (v != null) {
                    params.put(spec.getName(), v);
                }
            }
        }

        return params;
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

    /**
     * Map client response to the operation's declared outputParameters and return a JSON string to
     * send to the client. Returns null when mapping could not be applied and the caller should fall
     * back to the raw entity.
     * 
     * Handles conversion to JSON if outputRawFormat is specified.
     */
    private String mapOutputParameters(ApiServerOperationSpec serverOp, HandlingContext found)
            throws IOException {
        if (found == null || found.clientResponse == null
                || found.clientResponse.getEntity() == null) {
            return null;
        }

        JsonNode root = Converter.convertToJson(serverOp.getOutputRawFormat(),
                serverOp.getOutputSchema(), found.clientResponse.getEntity());

        for (OutputParameterSpec outputParameter : serverOp.getOutputParameters()) {
            if ("body".equalsIgnoreCase(inOrDefault(outputParameter))) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode mapped = Resolver.resolveOutputMappings(outputParameter, root, mapper);

                if (mapped != null && !(mapped instanceof NullNode)) {
                    return mapper.writeValueAsString(mapped);
                }
            }
        }

        return null;
    }

    /**
     * Return the `in` value for a spec, defaulting to "body" when missing.
     */
    private String inOrDefault(OutputParameterSpec spec) {
        if (spec == null)
            return "body";
        String in = spec.getIn();
        return in == null ? "body" : in;
    }

    private static class HandlingContext {
        HttpClientAdapter clientAdapter;
        Request clientRequest;
        Response clientResponse;

        void handle() {
            clientAdapter.getHttpClient().handle(clientRequest, clientResponse);
        }
    }

    public Capability getCapability() {
        return capability;
    }

    public ApiServerSpec getServerSpec() {
        return serverSpec;
    }

    public ApiServerResourceSpec getResourceSpec() {
        return resourceSpec;
    }

}
