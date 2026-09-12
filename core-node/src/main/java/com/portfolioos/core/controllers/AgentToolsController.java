package com.portfolioos.core.controllers;

import com.portfolioos.core.tools.PortfolioQueryTools;
import com.portfolioos.core.tools.ToolDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/tools")
public class AgentToolsController {

    private final PortfolioQueryTools portfolioQueryTools;

    public AgentToolsController(PortfolioQueryTools portfolioQueryTools) {
        this.portfolioQueryTools = portfolioQueryTools;
    }

    @GetMapping
    public ResponseEntity<List<ToolDtos.ToolDefinitionDto>> getTools() {
        return ResponseEntity.ok(portfolioQueryTools.getToolDefinitions());
    }

    @PostMapping("/execute")
    public ResponseEntity<ToolDtos.ToolExecutionResponse> executeTool(
        @RequestBody(required = false) ToolDtos.ToolExecutionRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(
                ToolDtos.ToolExecutionResponse.invalidParam(null, "Request body is required.")
            );
        }
        return ResponseEntity.ok(portfolioQueryTools.executeTool(request.tool(), request.arguments()));
    }
}
