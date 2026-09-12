package com.portfolioos.core.tools;

import com.portfolioos.core.common.PortfolioConstants;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.ReportDtos.PortfolioSummaryResponse;
import com.portfolioos.core.dtos.ReportDtos.HoldingDetailDto;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.TaxRulesLoader;
import com.portfolioos.core.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class PortfolioQueryTools {

    private static final Logger log = LoggerFactory.getLogger(PortfolioQueryTools.class);

    private final PortfolioValuationService valuationService;
    private final TaxOptimizationService taxService;
    private final SimulationService simulationService;
    private final DuckDbProjector duckDbProjector;
    private final LedgerCacheService cacheService;

    public PortfolioQueryTools(
        PortfolioValuationService valuationService,
        TaxOptimizationService taxService,
        SimulationService simulationService,
        DuckDbProjector duckDbProjector,
        LedgerCacheService cacheService
    ) {
        this.valuationService = valuationService;
        this.taxService = taxService;
        this.simulationService = simulationService;
        this.duckDbProjector = duckDbProjector;
        this.cacheService = cacheService;
    }

    public Map<String, Object> getPortfolioValuation() {
        log.info("LLM_TOOL_EXECUTION: tool=getPortfolioValuation params={}");
        String fy = TaxRulesLoader.detectFiscalYear(LocalDate.now());
        PortfolioSummaryResponse summary = valuationService.getPortfolioSummary(fy);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "getPortfolioValuation");
        result.put("fiscal_year", fy);
        result.put("total_net_worth", summary.totalCurrentValue());
        result.put("total_invested_cost", summary.totalInvested());
        result.put("total_unrealized_gain", summary.totalUnrealizedGain());
        result.put("portfolio_xirr", summary.xirrPercentage());
        result.put("active_holding_count", summary.activeHoldingCount());
        return result;
    }

    public Map<String, Object> getFundRegistry() {
        log.info("LLM_TOOL_EXECUTION: tool=getFundRegistry params={}");
        List<HoldingDetailDto> holdings = valuationService.getHoldings();
        Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(cacheService.getCachedState().fifoResult().openLots(), LocalDate.now());
        
        List<Map<String, Object>> registryList = new ArrayList<>();
        for (HoldingDetailDto h : holdings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("isin", h.assetId());
            entry.put("scheme_name", h.assetName());
            entry.put("category", h.category());
            entry.put("current_value", h.currentValue());
            entry.put("invested_value", h.investedValue());
            entry.put("unrealized_gain", h.unrealizedGain());
            entry.put("status", activeAssetIds.contains(h.assetId()) ? "ACTIVE_SIP" : "LEGACY_HOLDING");
            registryList.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "getFundRegistry");
        result.put("total_funds", registryList.size());
        result.put("funds", registryList);
        return result;
    }

    public Map<String, Object> getFireSummary() {
        log.info("LLM_TOOL_EXECUTION: tool=getFireSummary params={}");
        var state = cacheService.getCachedState();
        FireTracker.FireSummary fire = FireTracker.calculateFireSummary(state.fifoResult().openLots(), state.navMap(), LocalDate.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "getFireSummary");
        result.put("active_scenario_label", fire.activeScenarioLabel());
        result.put("monthly_expense_today", fire.monthlyExpenseToday());
        result.put("annual_expense", fire.annualExpense());
        result.put("required_fire_corpus", fire.requiredCorpus());
        result.put("total_net_worth", fire.totalNetWorth());
        result.put("fire_investable_net_worth", fire.fireInvestableNetWorth());
        result.put("years_remaining", fire.yearsRemaining());
        result.put("fire_status", fire.status());
        return result;
    }

    public Map<String, Object> getRebalancePlan() {
        log.info("LLM_TOOL_EXECUTION: tool=getRebalancePlan params={}");
        var state = cacheService.getCachedState();
        String fy = TaxRulesLoader.detectFiscalYear(LocalDate.now());
        BigDecimal currentVal = new BigDecimal(valuationService.getPortfolioSummary(fy).totalCurrentValue());
        BigDecimal personalNetWorthAth = duckDbProjector.getDailyNetWorthTrend().stream()
            .map(p -> BigDecimal.valueOf(p.valuation()))
            .max(BigDecimal::compareTo)
            .orElse(currentVal);

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            state.fifoResult().openLots(),
            state.fifoResult().matchedLots(),
            state.navMap(),
            LocalDate.now(),
            null,
            null,
            BucketConfigLoader.getActiveBucketTargets(LocalDate.now()),
            fy,
            "INDUCED",
            null
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "getRebalancePlan");
        result.put("fiscal_year", fy);
        result.put("derived_trigger_type", plan.trigger().type());
        result.put("plan_id", plan.planId());
        result.put("trigger", plan.trigger());
        result.put("sell_side", plan.sellSide());
        result.put("buy_side", plan.buySide());
        return result;
    }

    public Map<String, Object> getTaxHarvestOpportunities() {
        log.info("LLM_TOOL_EXECUTION: tool=getTaxHarvestOpportunities params={}");
        String fy = TaxRulesLoader.detectFiscalYear(LocalDate.now());
        ExemptionTracker.ExemptionStatus exemption = taxService.getExemptionStatus(fy);
        var harvestOps = taxService.getHarvestOpportunities();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "getTaxHarvestOpportunities");
        result.put("fiscal_year", fy);
        result.put("exemption_remaining", exemption.exemptionRemaining());
        result.put("taxable_ltcg_so_far", exemption.taxableLtcg());
        result.put("total_opportunities", harvestOps != null ? harvestOps.size() : 0);
        result.put("opportunities", harvestOps != null ? harvestOps : List.of());
        return result;
    }

    public Map<String, Object> getPairwiseFundOverlap(
        String fundA,
        String fundB
    ) {
        log.info("LLM_TOOL_EXECUTION: tool=getPairwiseFundOverlap params={fundA={}, fundB={}}", fundA, fundB);
        if (fundA == null || fundA.isBlank() || fundB == null || fundB.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "INVALID_PARAM");
            err.put("source_tool", "getPairwiseFundOverlap");
            err.put("message", "Both fundA and fundB ISIN parameters are required.");
            return err;
        }

        // Verify funds exist in registry
        List<HoldingDetailDto> holdings = valuationService.getHoldings();
        boolean existsA = holdings.stream().anyMatch(h -> h.assetId().equalsIgnoreCase(fundA) || h.assetName().toLowerCase().contains(fundA.toLowerCase()));
        boolean existsB = holdings.stream().anyMatch(h -> h.assetId().equalsIgnoreCase(fundB) || h.assetName().toLowerCase().contains(fundB.toLowerCase()));

        if (!existsA || !existsB) {
            String missing = !existsA ? fundA : fundB;
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "NOT_FOUND");
            err.put("source_tool", "getPairwiseFundOverlap");
            err.put("missing_entity", missing);
            err.put("message", "No fund matching '" + missing + "' exists in the active portfolio registry.");
            return err;
        }

        Map<String, Object> overlap = duckDbProjector.getPairwiseFundOverlap(fundA, fundB);
        overlap.put("status", "SUCCESS");
        overlap.put("source_tool", "getPairwiseFundOverlap");
        return overlap;
    }

    public Map<String, Object> simulateTrade(
        String isin,
        String schemeName,
        BigDecimal units,
        BigDecimal pricePerUnit,
        String tradeType
    ) {
        log.info("LLM_TOOL_EXECUTION: tool=simulateTrade params={isin={}, schemeName={}, units={}, pricePerUnit={}, tradeType={}}",
            isin, schemeName, units, pricePerUnit, tradeType);

        if (isin == null || isin.isBlank() || schemeName == null || schemeName.isBlank() ||
            units == null || units.compareTo(BigDecimal.ZERO) <= 0 ||
            pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0 ||
            tradeType == null || tradeType.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "INVALID_PARAM");
            err.put("source_tool", "simulateTrade");
            err.put("message", "Trade simulation requires valid parameters (isin, schemeName, positive units, pricePerUnit, tradeType). No arbitrary fallbacks are substituted.");
            return err;
        }

        SimulationService.TradeSimulationRequest simReq = new SimulationService.TradeSimulationRequest(
            isin,
            schemeName,
            units,
            pricePerUnit,
            null,
            tradeType.toUpperCase()
        );

        SimulationService.TradeSimulationResult res = simulationService.simulateTrade(simReq);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source_tool", "simulateTrade");
        result.put("simulation_result", res);
        result.put("notice", res.taxSummaryNotice());
        return result;
    }

    public List<ToolDtos.ToolDefinitionDto> getToolDefinitions() {
        return List.of(
            new ToolDtos.ToolDefinitionDto(
                "getPortfolioValuation",
                "Get real-time overall portfolio valuation, invested cost, unrealized gain, active scheme count, and money-weighted XIRR.",
                new ToolDtos.ToolParameterSchema("object", Map.of(), List.of())
            ),
            new ToolDtos.ToolDefinitionDto(
                "getFundRegistry",
                "Get list of registered mutual funds in the portfolio registry including ISIN codes, scheme names, asset classes, and active/legacy SIP status.",
                new ToolDtos.ToolParameterSchema("object", Map.of(), List.of())
            ),
            new ToolDtos.ToolDefinitionDto(
                "getFireSummary",
                "Calculate Financial Independence / Retire Early (FIRE) metrics including monthly expenses, annual burn rate, current corpus multiple, and projected FIRE target date.",
                new ToolDtos.ToolParameterSchema("object", Map.of(), List.of())
            ),
            new ToolDtos.ToolDefinitionDto(
                "getRebalancePlan",
                "Get point-in-time portfolio drawdown context, armed drawdown tier, and scheduled or induced rebalance sell-side & buy-side waterfall steps.",
                new ToolDtos.ToolParameterSchema("object", Map.of(), List.of())
            ),
            new ToolDtos.ToolDefinitionDto(
                "getTaxHarvestOpportunities",
                "Calculate tax-loss and tax-free gain harvest opportunities evaluated against remaining Sec 112A FY LTCG exemption headroom.",
                new ToolDtos.ToolParameterSchema("object", Map.of(), List.of())
            ),
            new ToolDtos.ToolDefinitionDto(
                "getPairwiseFundOverlap",
                "Calculate pairwise stock portfolio overlap percentage and common stock holdings between two mutual fund ISINs.",
                new ToolDtos.ToolParameterSchema(
                    "object",
                    Map.of(
                        "fundA", new ToolDtos.ToolPropertySchema("string", "Primary fund ISIN code (e.g. INF109KC13X2) or scheme name"),
                        "fundB", new ToolDtos.ToolPropertySchema("string", "Secondary fund ISIN code (e.g. INF879O01027) or scheme name")
                    ),
                    List.of("fundA", "fundB")
                )
            ),
            new ToolDtos.ToolDefinitionDto(
                "simulateTrade",
                "Simulate a what-if trade (DISPOSAL or ACQUISITION) to preview estimated capital gains tax drag, LTCG exemption headroom impact, and post-trade simulated state without persisting events.",
                new ToolDtos.ToolParameterSchema(
                    "object",
                    Map.of(
                        "isin", new ToolDtos.ToolPropertySchema("string", "Fund ISIN code (e.g. INF109KC13X2)"),
                        "schemeName", new ToolDtos.ToolPropertySchema("string", "Fund scheme name"),
                        "units", new ToolDtos.ToolPropertySchema("number", "Positive number of units to sell or buy"),
                        "pricePerUnit", new ToolDtos.ToolPropertySchema("number", "NAV / price per unit"),
                        "tradeType", new ToolDtos.ToolPropertySchema("string", "Trade action type", List.of("DISPOSAL", "ACQUISITION"))
                    ),
                    List.of("isin", "schemeName", "units", "pricePerUnit", "tradeType")
                )
            )
        );
    }

    public static BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public ToolDtos.ToolExecutionResponse executeTool(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.isBlank()) {
            return ToolDtos.ToolExecutionResponse.invalidParam(toolName, "Tool name cannot be null or blank.");
        }

        Map<String, Object> safeArgs = args != null ? args : Map.of();

        try {
            switch (toolName) {
                case "getPortfolioValuation" -> {
                    return ToolDtos.ToolExecutionResponse.success(toolName, getPortfolioValuation());
                }
                case "getFundRegistry" -> {
                    return ToolDtos.ToolExecutionResponse.success(toolName, getFundRegistry());
                }
                case "getFireSummary" -> {
                    return ToolDtos.ToolExecutionResponse.success(toolName, getFireSummary());
                }
                case "getRebalancePlan" -> {
                    return ToolDtos.ToolExecutionResponse.success(toolName, getRebalancePlan());
                }
                case "getTaxHarvestOpportunities" -> {
                    return ToolDtos.ToolExecutionResponse.success(toolName, getTaxHarvestOpportunities());
                }
                case "getPairwiseFundOverlap" -> {
                    Object fa = safeArgs.get("fundA");
                    Object fb = safeArgs.get("fundB");
                    if (fa == null || fb == null || fa.toString().isBlank() || fb.toString().isBlank()) {
                        return ToolDtos.ToolExecutionResponse.invalidParam(toolName, "Both fundA and fundB parameters are required.");
                    }
                    Map<String, Object> overlapRes = getPairwiseFundOverlap(fa.toString().trim(), fb.toString().trim());
                    if ("INVALID_PARAM".equals(overlapRes.get("status"))) {
                        return ToolDtos.ToolExecutionResponse.invalidParam(toolName, (String) overlapRes.get("message"));
                    }
                    if ("NOT_FOUND".equals(overlapRes.get("status"))) {
                        return ToolDtos.ToolExecutionResponse.notFound(toolName, (String) overlapRes.get("message"));
                    }
                    return ToolDtos.ToolExecutionResponse.success(toolName, overlapRes);
                }
                case "simulateTrade" -> {
                    String isin = safeArgs.get("isin") != null ? safeArgs.get("isin").toString().trim() : null;
                    String schemeName = safeArgs.get("schemeName") != null ? safeArgs.get("schemeName").toString().trim() : null;
                    BigDecimal units = parseBigDecimal(safeArgs.get("units"));
                    BigDecimal pricePerUnit = parseBigDecimal(safeArgs.get("pricePerUnit"));
                    String tradeType = safeArgs.get("tradeType") != null ? safeArgs.get("tradeType").toString().trim().toUpperCase() : null;

                    if (isin == null || isin.isBlank() || schemeName == null || schemeName.isBlank() ||
                        units == null || units.compareTo(BigDecimal.ZERO) <= 0 ||
                        pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0 ||
                        tradeType == null || (!tradeType.equals("DISPOSAL") && !tradeType.equals("ACQUISITION"))) {
                        return ToolDtos.ToolExecutionResponse.invalidParam(
                            toolName,
                            "Trade simulation requires valid parameters: non-blank isin, non-blank schemeName, positive numeric units, positive numeric pricePerUnit, and tradeType ('DISPOSAL' or 'ACQUISITION')."
                        );
                    }

                    Map<String, Object> simRes = simulateTrade(isin, schemeName, units, pricePerUnit, tradeType);
                    if ("INVALID_PARAM".equals(simRes.get("status"))) {
                        return ToolDtos.ToolExecutionResponse.invalidParam(toolName, (String) simRes.get("message"));
                    }
                    return ToolDtos.ToolExecutionResponse.success(toolName, simRes);
                }
                default -> {
                    return ToolDtos.ToolExecutionResponse.notFound(toolName, "Unknown tool: " + toolName);
                }
            }
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            return ToolDtos.ToolExecutionResponse.error(toolName, "Internal execution error: " + e.getMessage());
        }
    }
}
