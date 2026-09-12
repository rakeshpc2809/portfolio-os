package com.portfolioos.core.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ToolDtos {

    public record ToolPropertySchema(
        String type,
        String description,
        @JsonProperty("enum")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> enumValues
    ) {
        public ToolPropertySchema(String type, String description) {
            this(type, description, null);
        }
    }

    public record ToolParameterSchema(
        String type,
        Map<String, ToolPropertySchema> properties,
        List<String> required
    ) {}

    public record ToolDefinitionDto(
        String name,
        String description,
        ToolParameterSchema parameters
    ) {}

    public record ToolExecutionRequest(
        String tool,
        Map<String, Object> arguments
    ) {}

    public record ToolExecutionResponse(
        String tool,
        String status, // SUCCESS, INVALID_PARAM, NOT_FOUND, ERROR
        Object result,
        @JsonProperty("error_message")
        String errorMessage
    ) {
        public static ToolExecutionResponse success(String tool, Object result) {
            return new ToolExecutionResponse(tool, "SUCCESS", result, null);
        }

        public static ToolExecutionResponse invalidParam(String tool, String message) {
            return new ToolExecutionResponse(tool, "INVALID_PARAM", null, message);
        }

        public static ToolExecutionResponse notFound(String tool, String message) {
            return new ToolExecutionResponse(tool, "NOT_FOUND", null, message);
        }

        public static ToolExecutionResponse error(String tool, String message) {
            return new ToolExecutionResponse(tool, "ERROR", null, message);
        }
    }
}
