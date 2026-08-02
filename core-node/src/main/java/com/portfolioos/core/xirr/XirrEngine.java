package com.portfolioos.core.xirr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class XirrEngine {

    public double calculateXirr(List<CashFlow> cashFlows) {
        if (cashFlows.size() < 2) return 0.0;

        List<CashFlow> sorted = new ArrayList<>(cashFlows);
        sorted.sort(Comparator.comparing(CashFlow::date));

        LocalDate startDate = sorted.get(0).date();
        LocalDate endDate = sorted.get(sorted.size() - 1).date();
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalRealizedOrCurrent = BigDecimal.ZERO;

        for (CashFlow cf : sorted) {
            if (cf.amount().compareTo(BigDecimal.ZERO) < 0) {
                totalInvested = totalInvested.add(cf.amount().abs());
            } else if (cf.amount().compareTo(BigDecimal.ZERO) > 0) {
                totalRealizedOrCurrent = totalRealizedOrCurrent.add(cf.amount());
            }
        }

        if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return 0.0;

        if (totalDays < 30) {
            BigDecimal gain = totalRealizedOrCurrent.subtract(totalInvested);
            BigDecimal absReturn = gain.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100.0"));
            return absReturn.doubleValue();
        }

        List<Double> dates = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();

        for (CashFlow cf : sorted) {
            dates.add((double) ChronoUnit.DAYS.between(startDate, cf.date()) / 365.25);
            amounts.add(cf.amount().doubleValue());
        }

        // Newton-Raphson solver
        double rate = 0.10;
        for (int iter = 0; iter < 100; iter++) {
            double f = npv(rate, dates, amounts);
            double df = dNpv(rate, dates, amounts);

            if (Math.abs(df) > 1e-10) {
                double nextRate = rate - f / df;
                if (Math.abs(nextRate - rate) < 1e-7) {
                    double result = nextRate * 100.0;
                    if (Double.isNaN(result) || Double.isInfinite(result)) return 0.0;
                    return Math.max(-99.0, result);
                }
                rate = nextRate;
            }
            if (rate <= -0.90) rate = -0.50;
        }

        // Bracketed Bisection Fallback
        double low = -0.50;
        double high = 10.0;
        double flow = npv(low, dates, amounts);
        double fhigh = npv(high, dates, amounts);

        if (flow * fhigh <= 0) {
            for (int i = 0; i < 100; i++) {
                double mid = (low + high) / 2.0;
                double fmid = npv(mid, dates, amounts);
                if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
                    return Math.max(-99.0, mid * 100.0);
                }
                if (flow * fmid < 0) {
                    high = mid;
                    fhigh = fmid;
                } else {
                    low = mid;
                    flow = fmid;
                }
            }
            return Math.max(-99.0, ((low + high) / 2.0) * 100.0);
        }

        double rawResult = rate * 100.0;
        if (Double.isNaN(rawResult) || Double.isInfinite(rawResult)) return 0.0;
        return Math.max(-99.0, rawResult);
    }

    private double npv(double r, List<Double> dates, List<Double> amounts) {
        double sum = 0.0;
        for (int i = 0; i < dates.size(); i++) {
            double t = dates.get(i);
            double c = amounts.get(i);
            double factor = Math.pow(1.0 + r, t);
            if (factor != 0.0) {
                sum += c / factor;
            }
        }
        return sum;
    }

    private double dNpv(double r, List<Double> dates, List<Double> amounts) {
        double sum = 0.0;
        for (int i = 0; i < dates.size(); i++) {
            double t = dates.get(i);
            double c = amounts.get(i);
            double factor = Math.pow(1.0 + r, t + 1.0);
            if (factor != 0.0) {
                sum -= t * c / factor;
            }
        }
        return sum;
    }
}
