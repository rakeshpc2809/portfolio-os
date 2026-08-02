package com.portfolioos.core.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntigravityEngine {

    public record AssetFactorScore(
        String assetId,
        String assetName,
        BigDecimal beta,
        BigDecimal downsideBeta,
        BigDecimal zScore30d,
        BigDecimal twr30dPct,
        BigDecimal twr90dPct,
        boolean isAntigravity,
        String recommendation
    ) {}

    public record AntigravitySummary(
        String marketBenchmarkName,
        BigDecimal marketDrawdownPct,
        boolean isMarketCorrection,
        List<AssetFactorScore> antigravityAssets,
        List<AssetFactorScore> allAssetScores
    ) {}

    public static BigDecimal calculateBeta(List<Double> assetReturns, List<Double> marketReturns) {
        if (assetReturns.size() < 2 || assetReturns.size() != marketReturns.size()) {
            return BigDecimal.ONE;
        }

        int n = assetReturns.size();
        double meanAsset = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanMarket = marketReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double cov = 0.0;
        double varMarket = 0.0;

        for (int i = 0; i < n; i++) {
            double devAsset = assetReturns.get(i) - meanAsset;
            double devMarket = marketReturns.get(i) - meanMarket;
            cov += devAsset * devMarket;
            varMarket += devMarket * devMarket;
        }

        if (varMarket == 0.0) return BigDecimal.ONE;

        return BigDecimal.valueOf(cov / varMarket).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateDownsideBeta(List<Double> assetReturns, List<Double> marketReturns) {
        if (assetReturns.size() != marketReturns.size()) return BigDecimal.ONE;

        List<Double> downAsset = new ArrayList<>();
        List<Double> downMarket = new ArrayList<>();

        for (int i = 0; i < assetReturns.size(); i++) {
            double mRet = marketReturns.get(i);
            if (mRet < 0.0) {
                downAsset.add(assetReturns.get(i));
                downMarket.add(mRet);
            }
        }

        if (downAsset.size() < 2) return calculateBeta(assetReturns, marketReturns);

        double meanAsset = downAsset.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanMarket = downMarket.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double cov = 0.0;
        double varMarket = 0.0;

        for (int i = 0; i < downAsset.size(); i++) {
            double devAsset = downAsset.get(i) - meanAsset;
            double devMarket = downMarket.get(i) - meanMarket;
            cov += devAsset * devMarket;
            varMarket += devMarket * devMarket;
        }

        if (varMarket == 0.0) return BigDecimal.ONE;

        return BigDecimal.valueOf(cov / varMarket).setScale(2, RoundingMode.HALF_UP);
    }

    public static AntigravitySummary analyzePortfolioFactors(
        Map<String, List<Double>> assetReturnsMap,
        Map<String, String> assetNamesMap,
        List<Double> marketReturns,
        BigDecimal marketDrawdownPct
    ) {
        boolean isCorrection = marketDrawdownPct.compareTo(new BigDecimal("5.0")) >= 0;

        Map<String, Double> twr30dMap = new HashMap<>();
        List<Double> allTwr30d = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : assetReturnsMap.entrySet()) {
            List<Double> returns = entry.getValue();
            double twr = 0.0;
            if (!returns.isEmpty()) {
                int start = Math.max(0, returns.size() - 30);
                double compound = 1.0;
                for (int i = start; i < returns.size(); i++) {
                    compound *= (1.0 + returns.get(i));
                }
                twr = compound - 1.0;
            }
            twr30dMap.put(entry.getKey(), twr);
            allTwr30d.add(twr);
        }

        double meanTwr30d = allTwr30d.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double varianceSum = 0.0;
        for (double val : allTwr30d) {
            varianceSum += (val - meanTwr30d) * (val - meanTwr30d);
        }
        double stdDevTwr30d = (allTwr30d.size() > 1) ? Math.sqrt(varianceSum / (allTwr30d.size() - 1)) : 1.0;

        List<AssetFactorScore> scores = new ArrayList<>();
        List<AssetFactorScore> antigravityList = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : assetReturnsMap.entrySet()) {
            String assetId = entry.getKey();
            List<Double> returns = entry.getValue();

            BigDecimal beta = calculateBeta(returns, marketReturns);
            BigDecimal downsideBeta = calculateDownsideBeta(returns, marketReturns);

            double twr30 = twr30dMap.getOrDefault(assetId, 0.0);
            double zScore = (stdDevTwr30d > 0.0001) ? (twr30 - meanTwr30d) / stdDevTwr30d : 0.0;

            double twr90 = 0.0;
            if (!returns.isEmpty()) {
                int start = Math.max(0, returns.size() - 90);
                double compound = 1.0;
                for (int i = start; i < returns.size(); i++) {
                    compound *= (1.0 + returns.get(i));
                }
                twr90 = compound - 1.0;
            }

            BigDecimal twr30dBd = BigDecimal.valueOf(twr30 * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal twr90dBd = BigDecimal.valueOf(twr90 * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal zScoreBd = BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP);

            boolean isAntigravity = downsideBeta.compareTo(new BigDecimal("0.75")) < 0 
                                    && zScoreBd.compareTo(new BigDecimal("0.50")) > 0 
                                    && isCorrection;

            String recommendation;
            if (isAntigravity) {
                recommendation = "🚀 QUANT ANTIGRAVITY — Downside beta " + downsideBeta + " & Z-score +" + zScoreBd + ". Deploy dry powder here.";
                antigravityList.add(new AssetFactorScore(assetId, assetNamesMap.getOrDefault(assetId, assetId), beta, downsideBeta, zScoreBd, twr30dBd, twr90dBd, true, recommendation));
            } else if (downsideBeta.compareTo(new BigDecimal("0.75")) < 0) {
                recommendation = "Downside Cushion — Beta-minus " + downsideBeta + ".";
            } else if (zScoreBd.compareTo(new BigDecimal("0.50")) > 0) {
                recommendation = "Momentum Outperformer — Z-score +" + zScoreBd + ".";
            } else {
                recommendation = "Standard Market Beta.";
            }

            scores.add(new AssetFactorScore(
                assetId,
                assetNamesMap.getOrDefault(assetId, assetId),
                beta,
                downsideBeta,
                zScoreBd,
                twr30dBd,
                twr90dBd,
                isAntigravity,
                recommendation
            ));
        }

        return new AntigravitySummary(
            "Nifty 500 Index",
            marketDrawdownPct,
            isCorrection,
            antigravityList,
            scores
        );
    }
}
