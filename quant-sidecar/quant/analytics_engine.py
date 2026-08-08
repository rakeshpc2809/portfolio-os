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
