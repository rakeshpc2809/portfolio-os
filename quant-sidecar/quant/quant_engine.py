import numpy as np
import polars as pl
from hmmlearn import hmm
import logging
from typing import List, Tuple, Dict

logger = logging.getLogger(__name__)

def calculate_hurst_vectorized(ts: List[float]) -> float:
    """Vectorized Hurst Exponent calculation using Rescaled Range."""
    arr = np.array(ts)
    if len(arr) < 50:
        return 0.5
    
    lags = range(2, 20)
    tau = [np.sqrt(np.std(np.subtract(arr[lag:], arr[:-lag]))) for lag in lags]
    poly = np.polyfit(np.log(lags), np.log(tau), 1)
    return float(poly[0] * 2.0)

def calculate_ou_params_vectorized(navs: List[float]) -> dict:
    """Vectorized Ornstein-Uhlenbeck parameter estimation."""
    arr = np.array(navs)
    if len(arr) < 30:
        return {"half_life": 0.0, "valid": False}
    
    y = np.log(arr)
    x = y[:-1]
    dy = np.diff(y)
    
    # Regression: dy = (a + b*x)
    poly = np.polyfit(x, dy, 1)
    b, a = poly
    
    if b >= 0: # Non-stationary / diverging process
        return {"half_life": 0.0, "valid": False}
    
    theta = -b
    mu = -a / b
    half_life = np.log(2) / theta
    
    return {
        "theta": float(theta),
        "mu": float(mu),
        "half_life": float(half_life),
        "valid": True
    }

def calculate_hmm_regimes(returns_list: List[float], n_states: int = 3) -> Tuple[List[int], float, float, float]:
    """Fits HMM and returns states, bull probability, bear probability, and transit prob to bear."""
    if len(returns_list) < 50:
        return [0] * len(returns_list), 0.33, 0.33, 0.33
    
    try:
        data = np.array(returns_list).reshape(-1, 1)
        model = hmm.GaussianHMM(n_components=n_states, covariance_type="diag", n_iter=1000, random_state=42)
        model.fit(data)

        means = model.means_.flatten()
        # Sort indices by mean returns descending: [Bull, Neutral, Bear]
        sorted_indices = np.argsort(means)[::-1]
        rank_map = {orig_idx: rank for rank, orig_idx in enumerate(sorted_indices)}

        # Predictions
        states_raw = model.predict(data)
        states_mapped = [rank_map[s] for s in states_raw]
        curr_state_raw = states_raw[-1]

        # Probabilities
        probs_raw = model.predict_proba(data)[-1]
        bull_p = float(probs_raw[sorted_indices[0]])
        bear_p = float(probs_raw[sorted_indices[2]])

        # Transition matrix
        trans_mat_raw = model.transmat_
        to_bear_p = float(trans_mat_raw[curr_state_raw][sorted_indices[2]])

        return states_mapped, bull_p, bear_p, to_bear_p
    except Exception as e:
        logger.error(f"HMM fitting failed: {e}")
        return [0] * len(returns_list), 0.33, 0.33, 0.33
