import numpy as np
import pandas as pd
import logging

logger = logging.getLogger("quant.analytics_engine")
try:
    import quantstats as qs
except ImportError:
    qs = None

def compute_fund_analytics(nav_series, dates=None, benchmark_returns=None):
    if len(nav_series) < 30:
        return {
            "status": "INSUFFICIENT_HISTORY",
            "data_points": len(nav_series),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

    try:
        if dates is not None and len(dates) == len(nav_series) and any(d for d in dates if d):
            valid_pairs = [(nav, d) for nav, d in zip(nav_series, dates) if d]
            if len(valid_pairs) >= 10:
                vals, d_str = zip(*valid_pairs)
                idx = pd.to_datetime(d_str)
                s = pd.Series(vals, index=idx)
            else:
                s = pd.Series(nav_series)
        else:
            s = pd.Series(nav_series)

        returns = s.pct_change().dropna()

        if len(returns) < 10:
            return {
                "status": "INSUFFICIENT_HISTORY",
                "data_points": len(returns),
                "sharpe": 0.0,
                "sortino": 0.0,
                "calmar": 0.0,
                "max_drawdown": 0.0,
                "volatility_annual": 0.0,
                "var_95": 0.0,
                "cvar_95": 0.0,
                "beta": 0.0
            }

        if qs is not None:
            sharpe = float(qs.stats.sharpe(returns))
            sortino = float(qs.stats.sortino(returns))
            calmar = float(qs.stats.calmar(returns))
            max_dd = float(qs.stats.max_drawdown(returns))
            vol = float(qs.stats.volatility(returns))
            var95 = float(qs.stats.value_at_risk(returns))
            cvar95 = float(qs.stats.conditional_value_at_risk(returns))

            beta = 0.0
            if benchmark_returns is not None:
                try:
                    beta_val = qs.stats.greeks(returns, benchmark_returns).get("beta", 0.0)
                    beta = float(beta_val) if not np.isnan(beta_val) else 0.0
                except Exception:
                    beta = 0.0
        else:
            # Vectorized fallback calculation with true Downside Deviation Sortino ratio
            mean_ret = returns.mean()
            std_ret = returns.std()
            sharpe = float((mean_ret / std_ret) * np.sqrt(252)) if std_ret > 0 else 0.0
            
            downside_returns = returns[returns < 0]
            downside_std = downside_returns.std() if not downside_returns.empty else 0.0
            sortino = float((mean_ret / downside_std) * np.sqrt(252)) if downside_std > 0 else sharpe

            cum_returns = (1 + returns).cumprod()
            peak = cum_returns.cummax()
            dd = (cum_returns - peak) / peak
            max_dd = float(dd.min())
            calmar = float(mean_ret * 252 / abs(max_dd)) if abs(max_dd) > 0 else 0.0
            vol = float(std_ret * np.sqrt(252))
            var95 = float(returns.quantile(0.05))
            cvar95 = float(returns[returns <= var95].mean()) if not returns[returns <= var95].empty else var95
            beta = 0.0

        return {
            "status": "OK",
            "sharpe": 0.0 if np.isnan(sharpe) else round(sharpe, 2),
            "sortino": 0.0 if np.isnan(sortino) else round(sortino, 2),
            "calmar": 0.0 if np.isnan(calmar) else round(calmar, 2),
            "max_drawdown": 0.0 if np.isnan(max_dd) else round(max_dd, 4),
            "volatility_annual": 0.0 if np.isnan(vol) else round(vol, 4),
            "var_95": 0.0 if np.isnan(var95) else round(var95, 4),
            "cvar_95": 0.0 if np.isnan(cvar95) else round(cvar95, 4),
            "beta": 0.0 if np.isnan(beta) else round(beta, 2)
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "message": str(e),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

def run_monte_carlo_fire_simulation(
    daily_returns_list,
    current_corpus=1407122.81,
    annual_expense=720000.0,
    monthly_contribution=75000.0,
    years_to_retirement=13,
    retirement_duration_years=30,
    num_simulations=10000
):
    is_empirical = daily_returns_list is not None and len(daily_returns_list) >= 750
    if not is_empirical:
        returns = np.random.normal(loc=0.00045, scale=0.011, size=10000)
        returns = returns - returns.mean() + 0.00045
        data_source = "SYNTHETIC_MARKET_BENCHMARK"
        data_source_label = "Nifty 50 Historical Return Model (Insufficient Empirical History < 3 Years)"
    else:
        returns = np.array(daily_returns_list)
        data_source = "EMPIRICAL_PORTFOLIO"
        data_source_label = "Empirical Portfolio Return History (15-Day Block Bootstrap)"

    n_returns = len(returns)
    total_years = max(1, years_to_retirement) + max(1, retirement_duration_years)
    total_days = total_years * 252
    accumulation_days = max(1, years_to_retirement) * 252

    daily_sip = (monthly_contribution * 12.0) / 252.0
    daily_expense = annual_expense / 252.0

    block_size = min(15, n_returns)
    n_blocks_needed = int(np.ceil(total_days / block_size))

    start_indices = np.random.randint(0, n_returns, size=(num_simulations, n_blocks_needed))
    offsets = np.arange(block_size)
    sampled_blocks = (start_indices[:, :, None] + offsets[None, None, :]) % n_returns
    sim_returns = returns[sampled_blocks].reshape(num_simulations, -1)[:, :total_days]
    daily_inflation = 0.06 / 252.0
    real_sim_returns = sim_returns - daily_inflation

    logger.info(f"Realized simulation returns: daily_real_mean={real_sim_returns.mean():.6f}, annualized_real_mean={real_sim_returns.mean()*252:.4f}, annualized_std={real_sim_returns.std()*np.sqrt(252):.4f}")

    corpuses = np.full(num_simulations, float(current_corpus))
    failed = np.zeros(num_simulations, dtype=bool)

    # Accumulation Phase (compounding + SIP contributions in real terms)
    for day in range(accumulation_days):
        corpuses = corpuses * (1.0 + real_sim_returns[:, day]) + daily_sip

    retirement_corpuses = corpuses.copy()

    # Decumulation Phase (retirement spending in real terms)
    for day in range(accumulation_days, total_days):
        corpuses = corpuses * (1.0 + real_sim_returns[:, day]) - daily_expense
        failed = failed | (corpuses <= 0)
        corpuses = np.maximum(corpuses, 0.0)

    surviving = ~failed
    success_rate = float(np.mean(surviving) * 100.0)
    median_corpus = float(np.median(retirement_corpuses))
    p10_corpus = float(np.percentile(retirement_corpuses, 10))

    return {
        "status": "OK",
        "data_source": data_source,
        "data_source_label": data_source_label,
        "num_simulations": num_simulations,
        "years_to_retirement": years_to_retirement,
        "retirement_duration_years": retirement_duration_years,
        "success_rate_pct": round(success_rate, 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2)
    }


def compute_benchmark_analytics(portfolio_returns, benchmark_returns, benchmark_name="NIFTY_50_TRI"):
    p_rets = np.array(portfolio_returns, dtype=float)
    b_rets = np.array(benchmark_returns, dtype=float)

    if len(p_rets) == 0 or len(b_rets) == 0 or len(p_rets) != len(b_rets):
        return {
            "status": "ERROR",
            "message": "Mismatch or empty return series for benchmark analytics"
        }

    p_cagr = float(p_rets.mean() * 252.0 * 100.0)
    b_cagr = float(b_rets.mean() * 252.0 * 100.0)
    p_vol = float(p_rets.std() * np.sqrt(252.0) * 100.0)
    b_vol = float(b_rets.std() * np.sqrt(252.0) * 100.0)

    cov = float(np.cov(p_rets, b_rets)[0][1]) if len(p_rets) > 1 else 0.0
    var_b = float(np.var(b_rets)) if len(b_rets) > 1 else 0.0
    beta = round(cov / var_b, 3) if var_b > 0 else 1.0

    rf_pct = 6.50 # RBI 91-Day T-Bill Benchmark Rate
    alpha_ann = round(p_cagr - (rf_pct + beta * (b_cagr - rf_pct)), 2)
    tracking_err = round(float(np.std(p_rets - b_rets) * np.sqrt(252.0) * 100.0), 2)
    sharpe = round((p_cagr - rf_pct) / p_vol, 2) if p_vol > 0 else 0.0
    outperformance = round(p_cagr - b_cagr, 2)

    sample_days = len(p_rets)
    # ARCHITECTURAL DESIGN RATIONALE (WARNING BADGE vs HARD BLOCK):
    # Unlike Monte Carlo FIRE simulations (which resample returns across a 43-year lifetime horizon
    # and compound short-sample noise 29x into severe simulation distortions), benchmark risk analytics
    # (CAPM Alpha, Beta, Sharpe) compute static realized single-window statistics over aligned historical dates.
    # Short history introduces standard error/noise, but ZERO compounding amplification.
    # Therefore, a PROVISIONAL warning badge and visual UI styling (opacity/asterisk) is the methodologically
    # appropriate intervention, rather than hard-blocking or substituting synthetic benchmark data.
    is_provisional = sample_days < 750
    sample_status = "PROVISIONAL_SHORT_SAMPLE" if is_provisional else "MATURE_EMPIRICAL_SAMPLE"
    data_source_label = f"Provisional Benchmark Metrics (Short Sample: {sample_days} Days < 3 Years)" if is_provisional else "Mature Benchmark Risk Metrics (3+ Years History)"

    return {
        "status": "OK",
        "benchmark_name": benchmark_name,
        "sample_days": sample_days,
        "is_provisional": is_provisional,
        "sample_status": sample_status,
        "data_source_label": data_source_label,
        "risk_free_rate_pct": rf_pct,
        "portfolio_cagr_pct": round(p_cagr, 2),
        "benchmark_cagr_pct": round(b_cagr, 2),
        "portfolio_vol_pct": round(p_vol, 2),
        "benchmark_vol_pct": round(b_vol, 2),
        "alpha_pct": alpha_ann,
        "beta": beta,
        "sharpe_ratio": sharpe,
        "tracking_error_pct": tracking_err,
        "outperformance_pct": outperformance
    }
