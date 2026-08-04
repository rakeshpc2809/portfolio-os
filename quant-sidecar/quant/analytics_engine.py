import numpy as np
import pandas as pd
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
    current_corpus=1754783.21,
    annual_expense=600000.0,
    years=15,
    num_simulations=10000
):
    if daily_returns_list is None or len(daily_returns_list) < 10:
        return {
            "status": "INSUFFICIENT_DATA",
            "success_rate_pct": 95.0,
            "median_ending_corpus": current_corpus * 1.5,
            "tenth_percentile_corpus": current_corpus * 0.9
        }

    returns = np.array(daily_returns_list)
    trading_days = years * 252
    daily_expense = annual_expense / 252.0

    # Historical Bootstrapping: Randomly sample actual past daily returns with replacement to preserve true fat tails & skewness
    simulated_daily_returns = np.random.choice(returns, size=(num_simulations, trading_days), replace=True)

    surviving_sims = 0
    final_corpuses = []

    for sim_idx in range(num_simulations):
        corpus = current_corpus
        failed = False
        for day in range(trading_days):
            corpus = corpus * (1.0 + simulated_daily_returns[sim_idx, day]) - daily_expense
            if corpus <= 0:
                failed = True
                break
        if not failed:
            surviving_sims += 1
            final_corpuses.append(corpus)
        else:
            final_corpuses.append(0.0)

    success_rate = (surviving_sims / num_simulations) * 100.0
    median_corpus = float(np.median(final_corpuses))
    p10_corpus = float(np.percentile(final_corpuses, 10))

    return {
        "status": "OK",
        "num_simulations": num_simulations,
        "years": years,
        "success_rate_pct": round(success_rate, 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2)
    }
