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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.modelcontextprotocol.spec.McpSchema;
import io.naftiko.Capability;
import io.naftiko.engine.Resolver;
import io.naftiko.spec.OutputParameterSpec;
import io.naftiko.spec.exposes.McpServerToolSpec;

/**
 * Handles MCP tool calls by delegating to consumed HTTP operations.
 * 
 * Mirrors the logic in ApiOperationsRestlet but adapted for MCP tool invocations: - Input
 * parameters come from MCP CallToolRequest arguments (not HTTP request) - Supports both simple call
 * mode and full step orchestration - Returns McpSchema.CallToolResult (not HTTP response)
 */
public class McpToolHandler {

    private static final Logger logger = Logger.getLogger(McpToolHandler.class.getName());
    private final Capability capability;
    private final Map<String, McpServerToolSpec> toolSpecs;
    private final ObjectMapper mapper;
    private final OperationStepExecutor stepExecutor;

    public McpToolHandler(Capability capability, List<McpServerToolSpec> tools) {
        this.capability = capability;
        this.toolSpecs = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
        this.stepExecutor = new OperationStepExecutor(capability);

        for (McpServerToolSpec tool : tools) {
            toolSpecs.put(tool.getName(), tool);
        }
    }

    /**
     * Handle an MCP tool call.
     * 
     * @param toolName the name of the tool to invoke
     * @param arguments the tool input arguments (from MCP CallToolRequest)
     * @return the tool result
     */
    public McpSchema.CallToolResult handleToolCall(String toolName, Map<String, Object> arguments)
            throws Exception {

        McpServerToolSpec toolSpec = toolSpecs.get(toolName);
        if (toolSpec == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        // Merge arguments with tool-level 'with' parameters
        Map<String, Object> parameters = new HashMap<>();
        if (arguments != null) {
            parameters.putAll(arguments);
        }
        if (toolSpec.getWith() != null) {
            parameters.putAll(toolSpec.getWith());
        }

        OperationStepExecutor.HandlingContext found = null;

        if (toolSpec.getCall() != null) {
            // Simple call mode
            found = stepExecutor.findClientRequestFor(toolSpec.getCall(), parameters);

            if (found != null) {
                try {
                    found.handle();
                } catch (Exception e) {
                    logger.warning("Error during HTTP client call for tool '" + toolName + "': " + e);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                    "Error during HTTP client call: " + e.getMessage())),
                            true, null, null);
                }
            } else {
                throw new IllegalArgumentException("Invalid call format: "
                        + (toolSpec.getCall() != null ? toolSpec.getCall().getOperation()
                                : "null"));
            }
        } else if (toolSpec.getSteps() != null && !toolSpec.getSteps().isEmpty()) {
            // Step orchestration mode
            OperationStepExecutor.StepExecutionResult stepResult =
                    stepExecutor.executeSteps(toolSpec.getSteps(), parameters);
            found = stepResult.lastContext;
        } else {
            throw new IllegalArgumentException(
                    "Tool '" + toolName + "' has neither call nor steps defined");
        }

        // Map the response to MCP CallToolResult
        return buildToolResult(toolSpec, found);
    }

    /**
     * Build an MCP CallToolResult from the HTTP client response.
     */
    private McpSchema.CallToolResult buildToolResult(McpServerToolSpec toolSpec,
            OperationStepExecutor.HandlingContext found) throws IOException {

        if (found == null) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                            "No response received: no matching client adapter found")),
                    true, null, null);
        }

        if (found.clientResponse == null) {
            return new McpSchema.CallToolResult(List
                    .of(new McpSchema.TextContent("No response received: client response is null")),
                    true, null, null);
        }

        // Check for error status
        int statusCode = found.clientResponse.getStatus().getCode();
        boolean isError = statusCode >= 400;

        if (found.clientResponse.getEntity() == null) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                            "No response entity received (HTTP " + statusCode + " "
                                    + found.clientResponse.getStatus().getReasonPhrase() + ")")),
                    true, null, null);
        }

        // Buffer entity text before any mapping to avoid double-read issues
        String responseText = found.clientResponse.getEntity().getText();

        // Apply output parameter mappings if defined
        if (toolSpec.getOutputParameters() != null && !toolSpec.getOutputParameters().isEmpty()
                && responseText != null && !responseText.isEmpty()) {
            String mapped = mapOutputParameters(toolSpec, responseText);
            if (mapped != null) {
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(mapped)),
                        isError, null, null);
            }
        }

        // Fall back to raw response
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(responseText != null ? responseText : "")),
                isError, null, null);
    }

    /**
     * Map pre-buffered response text to the tool's declared outputParameters and return a JSON
     * string. Returns null when mapping could not be applied.
     */
    private String mapOutputParameters(McpServerToolSpec toolSpec, String responseText)
            throws IOException {
        if (responseText == null || responseText.isEmpty()) {
            return null;
        }

        JsonNode root = mapper.readTree(responseText);

        for (OutputParameterSpec outputParameter : toolSpec.getOutputParameters()) {
            JsonNode mapped = Resolver.resolveOutputMappings(outputParameter, root, mapper);

            if (mapped != null && !(mapped instanceof NullNode)) {
                return mapper.writeValueAsString(mapped);
            }
        }

        return null;
    }

    public Capability getCapability() {
        return capability;
    }

}
