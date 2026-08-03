package com.portfolioos.core.controllers;

import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.SimulationService.TradeSimulationRequest;
import com.portfolioos.core.service.SimulationService.TradeSimulationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulate")
public class SimulatorController {

    private final SimulationService simulationService;

    public SimulatorController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/trade")
    public ResponseEntity<TradeSimulationResult> simulateTrade(
        @RequestBody TradeSimulationRequest req
    ) {
        return ResponseEntity.ok(simulationService.simulateTrade(req));
    }
}
