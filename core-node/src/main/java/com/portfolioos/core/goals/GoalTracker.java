package com.portfolioos.core.goals;

import com.portfolioos.core.model.Lot;
import com.portfolioos.core.valuation.BucketEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoalTracker {

    public enum GoalTag {
        EMERGENCY,
        BIKE,
        WEDDING,
        RETIREMENT,
        UNALLOCATED
    }

    public record GoalAllocation(
        String holdingId,
        String holdingName,
        GoalTag goalTag,
        BigDecimal allocatedAmount
    ) {}

    public record GoalSummary(
        BigDecimal totalLiquidHoldings,
        BigDecimal allocatedGoalsAmount,
        BigDecimal unallocatedCash,
        Map<GoalTag, BigDecimal> allocationsByGoal,
        List<GoalAllocation> goalAllocations
    ) {}

    public static final List<GoalAllocation> DEFAULT_ALLOCATIONS = List.of(
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.EMERGENCY, new BigDecimal("150000.00")),
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.BIKE, new BigDecimal("100000.00")),
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.WEDDING, new BigDecimal("100000.00"))
    );

    public static GoalSummary calculateGoalSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        List<GoalAllocation> customAllocations,
        BigDecimal bankBalance
    ) {
        BigDecimal totalLiquidMF = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BucketEngine.Bucket bucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
            if (bucket == BucketEngine.Bucket.LIQUID_BUFFER) {
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "GoalTracker");
                totalLiquidMF = totalLiquidMF.add(lot.remainingUnits().multiply(nav));
            }
        }
        BigDecimal totalLiquidHoldings = totalLiquidMF.add(bankBalance);

        Map<GoalTag, BigDecimal> allocatedMap = new HashMap<>();
        for (GoalTag tag : GoalTag.values()) {
            allocatedMap.put(tag, BigDecimal.ZERO);
        }

        BigDecimal totalAllocatedNonUnallocated = BigDecimal.ZERO;
        for (GoalAllocation alloc : customAllocations) {
            BigDecimal cur = allocatedMap.getOrDefault(alloc.goalTag(), BigDecimal.ZERO);
            allocatedMap.put(alloc.goalTag(), cur.add(alloc.allocatedAmount()));

            if (alloc.goalTag() != GoalTag.UNALLOCATED) {
                totalAllocatedNonUnallocated = totalAllocatedNonUnallocated.add(alloc.allocatedAmount());
            }
        }

        BigDecimal unallocatedCash = totalLiquidHoldings.subtract(totalAllocatedNonUnallocated).max(BigDecimal.ZERO);
        allocatedMap.put(GoalTag.UNALLOCATED, unallocatedCash);

        Map<GoalTag, BigDecimal> formattedAllocationsByGoal = new HashMap<>();
        for (Map.Entry<GoalTag, BigDecimal> entry : allocatedMap.entrySet()) {
            formattedAllocationsByGoal.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP));
        }

        return new GoalSummary(
            totalLiquidHoldings.setScale(2, RoundingMode.HALF_UP),
            totalAllocatedNonUnallocated.setScale(2, RoundingMode.HALF_UP),
            unallocatedCash.setScale(2, RoundingMode.HALF_UP),
            formattedAllocationsByGoal,
            customAllocations
        );
    }

    public static GoalSummary calculateGoalSummary(List<Lot> openLots, Map<String, BigDecimal> navMap) {
        return calculateGoalSummary(openLots, navMap, DEFAULT_ALLOCATIONS, BigDecimal.ZERO);
    }
}
