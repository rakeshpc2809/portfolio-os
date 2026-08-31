import { API_BASE, fetchJson } from "../../api.js?t=1788114000";
import { FUND_REGISTRY } from "../../constants.js?t=1788114000";
import { state } from "../../state.js?t=1788114000";
import { formatINR, shortenFundName } from "../../utils.js?t=1788114000";

export function renderPieChart(containerId, data) {
  const container = document.getElementById(containerId);
  if (!container || !data || data.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: "transparent",
    tooltip: { trigger: "item", formatter: "{b}: ₹ {c} ({d}%)" },
    legend: {
      orient: "vertical",
      right: 10,
      top: "center",
      textStyle: { color: "#94a3b8", fontSize: 11 },
    },
    series: [
      {
        type: "pie",
        radius: ["40%", "75%"],
        center: ["38%", "50%"],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: "#0c101c", borderWidth: 2 },
        label: { show: false },
        data: data,
      },
    ],
  };
  instance.setOption(option);

  if (window.ResizeObserver && !container.__resizeObserverAttached) {
    container.__resizeObserverAttached = true;
    const ro = new ResizeObserver(() => {
      try {
        instance.resize();
      } catch (e) {}
    });
    ro.observe(container);
  }

  return instance;
}

export function resampleToMonthEnd(dates, values, investedValues) {
  if (!dates || dates.length === 0) return { dates: [], values: [], investedValues: [] };

  const monthMap = new Map();
  for (let i = 0; i < dates.length; i++) {
    const dStr = dates[i];
    const monthKey = dStr.substring(0, 7); // YYYY-MM
    monthMap.set(monthKey, {
      date: dStr,
      value: values[i],
      invested: investedValues && investedValues.length > i ? investedValues[i] : 0,
    });
  }

  const allResampled = Array.from(monthMap.values());
  const sliced = allResampled.slice(-12);

  const resDates = sliced.map((p) => p.date);
  const resValues = sliced.map((p) => p.value);
  const resInvested = sliced.map((p) => p.invested);

  const windowBadge = document.getElementById("netWorthWindowBadge");
  if (windowBadge) {
    windowBadge.textContent = `Trailing ${sliced.length} Months (Month-End Snapshot)`;
  }

  return { dates: resDates, values: resValues, investedValues: resInvested };
}

export function renderNetWorthTrendChart(
  containerId,
  dates,
  values,
  investedValues = null,
  isMonthly = false,
) {
  const container = document.getElementById(containerId);
  if (!container || !dates || dates.length === 0 || !window.echarts) return null;

  if (state.charts.netWorthTrendChart) {
    try {
      state.charts.netWorthTrendChart.dispose();
    } catch (e) {}
  }

  const instance = window.echarts.init(container);

  // Calculate MoM % if monthly or latest period change
  if (values && values.length >= 2) {
    const prevVal = values[values.length - 2];
    const currVal = values[values.length - 1];
    if (prevVal > 0) {
      const momPct = ((currVal - prevVal) / prevVal) * 100;
      const momBadge = document.getElementById("netWorthMoMBadge");
      if (momBadge) {
        const sign = momPct >= 0 ? "+" : "";
        momBadge.textContent = `MoM: ${sign}${momPct.toFixed(1)}%`;
        if (momPct >= 0) {
          momBadge.style.background = "rgba(16, 185, 129, 0.15)";
          momBadge.style.color = "#10b981";
          momBadge.style.borderColor = "#10b981";
        } else {
          momBadge.style.background = "rgba(239, 68, 68, 0.15)";
          momBadge.style.color = "#ef4444";
          momBadge.style.borderColor = "#ef4444";
        }
      }
    }
  }

  const series = [
    {
      name: "Net Worth",
      type: "line",
      smooth: true,
      showSymbol: isMonthly,
      symbolSize: 6,
      lineStyle: { width: 3, color: "#d0ff00" },
      areaStyle: {
        color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: "rgba(208,255,0,0.25)" },
          { offset: 1, color: "rgba(6,182,212,0.01)" },
        ]),
      },
      data: values,
    },
  ];

  if (investedValues && investedValues.length > 0) {
    series.push({
      name: "Capital Invested",
      type: "line",
      smooth: true,
      z: 10,
      showSymbol: isMonthly,
      symbolSize: 6,
      lineStyle: { width: 2.5, color: "#38bdf8", type: "dashed" },
      data: investedValues,
    });
  }

  const option = {
    backgroundColor: "transparent",
    legend: {
      show: true,
      top: "0%",
      right: "2%",
      textStyle: { color: "#cbd5e1", fontSize: 11 },
      data: ["Net Worth", "Capital Invested"],
    },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross", label: { backgroundColor: "#090f1e" } },
      formatter: (params) => {
        let res = `<div style="font-weight:700; color:#f8fafc; margin-bottom:4px;">${params[0].name}</div>`;
        params.forEach((p) => {
          const color = p.color || "#38bdf8";
          res += `<div><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:6px;"></span>${p.seriesName}: <b>₹ ${formatINR(p.value)}</b></div>`;
        });
        if (isMonthly && params[0].dataIndex > 0) {
          const idx = params[0].dataIndex;
          const pVal = values[idx - 1];
          const cVal = values[idx];
          if (pVal > 0) {
            const diff = cVal - pVal;
            const pct = (diff / pVal) * 100;
            const sign = pct >= 0 ? "+" : "";
            res += `<div style="margin-top:4px; font-size:0.75rem; color:#cbd5e1;">MoM Return: <b style="color:${pct >= 0 ? "#10b981" : "#ef4444"};">${sign}${pct.toFixed(1)}% (${sign}₹ ${formatINR(diff)})</b></div>`;
          }
        }
        return res;
      },
    },
    grid: { left: "3%", right: "3%", top: "16%", bottom: "16%", containLabel: true },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: "rgba(255,255,255,0.15)" } },
      axisLabel: { color: "#94a3b8", fontSize: 10, hideOverlap: true },
    },
    yAxis: {
      type: "value",
      axisLine: { show: false },
      splitLine: { lineStyle: { color: "rgba(255,255,255,0.05)" } },
      axisLabel: {
        color: "#94a3b8",
        fontSize: 11,
        formatter: (v) => `₹ ${(v / 100000).toFixed(1)}L`,
      },
    },
    dataZoom: [
      { type: "inside", start: 0, end: 100 },
      {
        type: "slider",
        start: 0,
        end: 100,
        height: 16,
        bottom: 0,
        borderColor: "transparent",
        backgroundColor: "rgba(255,255,255,0.05)",
        fillerColor: "rgba(208,255,0,0.2)",
      },
    ],
    series: series,
  };
  instance.setOption(option);
  state.charts.netWorthTrendChart = instance;

  // Handle Dynamic ResizeObserver for parent container
  if (window.ResizeObserver && container) {
    if (container._resizeObserver) {
      container._resizeObserver.disconnect();
    }
    container._resizeObserver = new ResizeObserver(() => {
      try {
        instance.resize();
      } catch (e) {}
    });
    container._resizeObserver.observe(container);
  }

  return instance;
}

export async function loadNetWorthTrend(isMonthly = false) {
  try {
    const data =
      (await fetchJson(`${API_BASE}/reports/trend`).catch(() => null)) ||
      (await fetchJson(`${API_BASE}/portfolio/net-worth-trend`).catch(() => null));
    if (!data?.dates || data.dates.length === 0) return;

    state.netWorthRawData = data;

    let dates = data.dates;
    let values = data.values;
    let investedValues = data.invested_values || data.investedValues || [];
    const coverage = typeof data.coverage_pct === "number" ? data.coverage_pct : 100.0;

    if (isMonthly) {
      const resampled = resampleToMonthEnd(dates, values, investedValues);
      dates = resampled.dates;
      values = resampled.values;
      investedValues = resampled.investedValues;
    } else {
      const windowBadge = document.getElementById("netWorthWindowBadge");
      if (windowBadge) {
        windowBadge.textContent =
          coverage >= 99.0
            ? "Daily Valuation & Capital Contributed (100% Mark-to-Market NAV)"
            : `Daily Valuation & Capital Contributed (${coverage.toFixed(1)}% Value-Weighted NAV Coverage)`;
      }
    }

    renderNetWorthTrendChart("netWorthChartContainer", dates, values, investedValues, isMonthly);
  } catch (err) {
    console.error("Failed to load Net Worth Trend:", err);
  }
}

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;

  const total = allocations.reduce(
    (sum, a) => sum + (parseFloat(a.current_value || a.currentValue) || 0),
    0,
  );

  const main = [];
  let othersVal = 0;
  let othersCount = 0;

  allocations.forEach((a) => {
    const val = parseFloat(a.current_value || a.currentValue) || 0;
    const assetName = a.asset_name || a.assetName || "";
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: shortenFundName(assetName),
        value: val,
      });
    }
  });

  if (othersVal > 0) {
    main.push({
      name: `Others (${othersCount})`,
      value: othersVal,
    });
  }

  state.charts.allocChart = renderPieChart("allocationChart", main);
}

export function renderCategoryChart(catAllocations) {
  if (state.charts.categoryChart) state.charts.categoryChart.dispose();

  const data = catAllocations.map((c) => ({
    name: c.category_name || c.categoryName,
    value: parseFloat(c.current_value || c.currentValue) || 0,
  }));

  state.charts.categoryChart = renderPieChart("categoryChart", data);
}

export function renderBucketAllocationChart(containerId, bucketStatuses) {
  const container = document.getElementById(containerId);
  if (!container || !bucketStatuses || bucketStatuses.length === 0 || !window.echarts) return null;

  if (state.charts.bucketAllocChart) {
    try {
      state.charts.bucketAllocChart.dispose();
    } catch (e) {}
  }

  container.style.height = "360px";
  const instance = window.echarts.init(container);

  const categories = bucketStatuses.map((b) => {
    const raw = b.bucket_name || b.bucketName || b.bucket || "";
    return raw
      .replace(/_/g, " ")
      .toLowerCase()
      .replace(/\b\w/g, (l) => l.toUpperCase());
  });

  const targetData = bucketStatuses.map((b) => parseFloat(b.target_pct || b.targetPct) || 0);
  const actualData = bucketStatuses.map((b) => {
    const val = parseFloat(b.current_pct || b.currentPct) || 0;
    const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
    const isLegacy = (b.bucket_name || b.bucketName || b.bucket) === "LEGACY_HOLDINGS";
    return {
      value: val,
      itemStyle: {
        color: isLegacy ? "#64748b" : isDrifted ? "#f59e0b" : "#10b981",
      },
    };
  });

  const option = {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        let res = `<div style="font-weight:600; color:#f8fafc; margin-bottom:4px;">${params[0].name}</div>`;
        params.forEach((p) => {
          res += `${p.marker} ${p.seriesName}: <b>${p.value.toFixed(2)}%</b><br/>`;
        });
        return res;
      },
    },
    legend: {
      data: ["Target %", "Actual %"],
      textStyle: { color: "#94a3b8", fontSize: 12 },
      right: 15,
      top: 10,
    },
    grid: { left: "3%", right: "4%", bottom: "5%", top: "45px", containLabel: true },
    xAxis: {
      type: "category",
      data: categories,
      axisLine: { lineStyle: { color: "#334155" } },
      axisLabel: { color: "#f1f5f9", fontSize: 11, fontWeight: 500, interval: 0, rotate: 0 },
    },
    yAxis: {
      type: "value",
      axisLine: { lineStyle: { color: "#334155" } },
      splitLine: { lineStyle: { color: "rgba(255, 255, 255, 0.06)" } },
      axisLabel: { color: "#94a3b8", fontSize: 11, formatter: "{value}%" },
    },
    series: [
      {
        name: "Target %",
        type: "bar",
        barWidth: 16,
        data: targetData,
        itemStyle: { color: "#38bdf8", borderRadius: [4, 4, 0, 0] },
        label: {
          show: true,
          position: "top",
          formatter: "{c}%",
          color: "#38bdf8",
          fontSize: 10,
          fontWeight: 600,
        },
        barGap: "30%",
      },
      {
        name: "Actual %",
        type: "bar",
        barWidth: 16,
        data: actualData,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        label: {
          show: true,
          position: "top",
          formatter: "{c}%",
          color: "#cbd5e1",
          fontSize: 10,
          fontWeight: 600,
        },
        barGap: "30%",
      },
    ],
  };

  instance.setOption(option);
  state.charts.bucketAllocChart = instance;
  return instance;
}

export function renderFundAllocationCompareChart(containerId, holdings, bucketTargetsConfig) {
  const container = document.getElementById(containerId);
  if (!container || !window.echarts) return null;

  if (state.charts.fundAllocCompareChart) {
    try {
      state.charts.fundAllocCompareChart.dispose();
    } catch (e) {}
  }

  // 1. Extract active target version (e.g. v2.0)
  let activeVersion = null;
  if (bucketTargetsConfig?.versions && bucketTargetsConfig.versions.length > 0) {
    activeVersion = bucketTargetsConfig.versions[bucketTargetsConfig.versions.length - 1];
  }

  // 2. Build planned map: fund_id -> planned_pct
  const plannedMap = {};
  const fundNameMap = {};

  if (activeVersion?.targets) {
    activeVersion.targets.forEach((t) => {
      const bucketTargetPct = parseFloat(t.target_pct || t.targetPct) || 0;
      const prefFunds = t.preferred_funds || t.preferredFunds || [];
      prefFunds.forEach((pf) => {
        const isin = pf.fund_id || pf.fundId;
        const name = pf.fund_name || pf.fundName;
        const weight = parseFloat(pf.allocation_weight || pf.allocationWeight) || 0;
        const plannedPct = Math.round(bucketTargetPct * weight * 100) / 100;
        if (isin) {
          plannedMap[isin] = plannedPct;
          if (name) fundNameMap[isin] = name;
        }
      });
    });
  }

  // 3. Build total portfolio net worth & actual map: fund_id -> actual_pct
  const totalVal = (holdings || []).reduce(
    (sum, h) => sum + (parseFloat(h.current_value || h.currentValue) || 0),
    0,
  );
  const actualMap = {};
  const isinList = new Set();

  (holdings || []).forEach((h) => {
    const isin = h.asset_id || h.assetId;
    const name = h.asset_name || h.assetName;
    const val = parseFloat(h.current_value || h.currentValue) || 0;
    const actualPct = totalVal > 0 ? Math.round((val / totalVal) * 10000) / 100 : 0;
    if (isin) {
      actualMap[isin] = actualPct;
      fundNameMap[isin] = name || fundNameMap[isin] || isin;
      isinList.add(isin);
    }
  });

  // Add any target ISINs that aren't in holdings yet
  Object.keys(plannedMap).forEach((isin) => {
    isinList.add(isin);
  });

  // 4. Create combined items array
  const items = Array.from(isinList).map((isin) => {
    const name = fundNameMap[isin] || isin;
    const plannedPct = plannedMap[isin] || 0;
    const actualPct = actualMap[isin] || 0;
    const isTarget = plannedPct > 0;
    const drift = Math.round((actualPct - plannedPct) * 100) / 100;
    return {
      isin,
      name,
      shortName: shortenFundName(name),
      plannedPct,
      actualPct,
      drift,
      isTarget,
    };
  });

  // Dynamic height calculation so bars are never squished or unreadable
  const minHeight = 380;
  const calculatedHeight = Math.max(minHeight, items.length * 42 + 70);
  container.style.height = `${calculatedHeight}px`;

  const instance = window.echarts.init(container);

  // Sort: Target funds first (by plannedPct asc for bottom-to-top rendering in horizontal bar), then legacy funds
  items.sort((a, b) => {
    if (a.isTarget && !b.isTarget) return 1;
    if (!a.isTarget && b.isTarget) return -1;
    if (a.isTarget && b.isTarget) return a.plannedPct - b.plannedPct;
    return a.actualPct - b.actualPct;
  });

  const categories = items.map((i) => i.shortName);
  const plannedData = items.map((i) => i.plannedPct);
  const actualData = items.map((i) => ({
    value: i.actualPct,
    itemStyle: {
      color: !i.isTarget ? "#64748b" : Math.abs(i.drift) > 5.0 ? "#f59e0b" : "#10b981",
    },
  }));

  const option = {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        const index = params[0].dataIndex;
        const item = items[index];
        let res = `<div style="font-weight:600; color:#f8fafc; margin-bottom:4px;">${item.name}</div>`;
        res += `<span style="color:#94a3b8; font-size:11px;">ISIN: ${item.isin}</span><br/>`;
        params.forEach((p) => {
          res += `${p.marker} ${p.seriesName}: <b>${p.value.toFixed(2)}%</b><br/>`;
        });
        const driftSign = item.drift >= 0 ? "+" : "";
        const driftColor = item.drift > 5 ? "#f59e0b" : item.drift < -5 ? "#ef4444" : "#10b981";
        res += `Drift (&Delta;): <b style="color:${driftColor}">${driftSign}${item.drift.toFixed(2)}%</b>`;
        return res;
      },
    },
    legend: {
      data: ["Planned %", "Actual %"],
      textStyle: { color: "#94a3b8", fontSize: 12 },
      right: 15,
      top: 10,
    },
    grid: { left: "220px", right: "8%", bottom: "4%", top: "45px", containLabel: false },
    xAxis: {
      type: "value",
      axisLine: { lineStyle: { color: "#334155" } },
      splitLine: { lineStyle: { color: "rgba(255, 255, 255, 0.06)" } },
      axisLabel: { color: "#94a3b8", fontSize: 11, formatter: "{value}%" },
    },
    yAxis: {
      type: "category",
      data: categories,
      axisLine: { lineStyle: { color: "#334155" } },
      axisLabel: {
        color: "#f1f5f9",
        fontSize: 11,
        fontWeight: 500,
        width: 200,
        overflow: "truncate",
        ellipsis: "...",
      },
    },
    series: [
      {
        name: "Planned %",
        type: "bar",
        barWidth: 12,
        data: plannedData,
        itemStyle: { color: "#38bdf8", borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: "right",
          formatter: "{c}%",
          color: "#38bdf8",
          fontSize: 10,
          fontWeight: 600,
        },
        barGap: "25%",
      },
      {
        name: "Actual %",
        type: "bar",
        barWidth: 12,
        data: actualData,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: "right",
          formatter: "{c}%",
          color: "#cbd5e1",
          fontSize: 10,
          fontWeight: 600,
        },
        barGap: "25%",
      },
    ],
  };

  instance.setOption(option);
  state.charts.fundAllocCompareChart = instance;
  return instance;
}



if (typeof document !== "undefined") {
  document.addEventListener("DOMContentLoaded", () => {
    const btnDaily = document.getElementById("btnNetWorthDaily");
    const btnMonthly = document.getElementById("btnNetWorthMonthly");

    if (btnDaily && btnMonthly) {
      btnDaily.addEventListener("click", () => {
        btnDaily.classList.add("active");
        btnDaily.style.background = "rgba(56, 189, 248, 0.2)";
        btnDaily.style.color = "#38bdf8";
        btnDaily.style.borderColor = "#38bdf8";

        btnMonthly.classList.remove("active");
        btnMonthly.style.background = "rgba(255,255,255,0.05)";
        btnMonthly.style.color = "#94a3b8";
        btnMonthly.style.borderColor = "rgba(255,255,255,0.1)";

        loadNetWorthTrend(false);
      });

      btnMonthly.addEventListener("click", () => {
        btnMonthly.classList.add("active");
        btnMonthly.style.background = "rgba(56, 189, 248, 0.2)";
        btnMonthly.style.color = "#38bdf8";
        btnMonthly.style.borderColor = "#38bdf8";

        btnDaily.classList.remove("active");
        btnDaily.style.background = "rgba(255,255,255,0.05)";
        btnDaily.style.color = "#94a3b8";
        btnDaily.style.borderColor = "rgba(255,255,255,0.1)";

        loadNetWorthTrend(true);
      });
    }
    loadNetWorthTrend(false);
  });
}
