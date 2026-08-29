local snapshotData = nil

local function renderWidget()
    barWidget.setGlyph("chart-line")
    
    if not snapshotData then
        barWidget.setText("Loading...")
        barWidget.setTooltip("Fetching Portfolio OS status...")
        return
    end

    if not snapshotData.ok then
        barWidget.setText("Error")
        barWidget.setTooltip("Portfolio OS Error: " .. (snapshotData.error or "Unknown error"))
        return
    end

    -- 1. Minimal Bar Text: Net Worth + XIRR
    local netWorthText = snapshotData.total_net_worth_compact or "₹0"
    local xirrText = snapshotData.xirr or ""

    if xirrText ~= "" and xirrText ~= "N/A" then
        barWidget.setText(netWorthText .. " (" .. xirrText .. ")")
    else
        barWidget.setText(netWorthText)
    end

    -- 2. Noctalia Native Two-Column Tooltip (Key Value separated without colons)
    local lines = {}
    
    -- Summary Section
    local netWorthStr = snapshotData.total_net_worth_compact or "N/A"
    local xirrValStr = (xirrText ~= "" and xirrText ~= "N/A") and xirrText or "N/A"
    
    table.insert(lines, string.format("%-20s %s", "Net Worth", netWorthStr))
    table.insert(lines, string.format("%-20s %s", "XIRR Return", xirrValStr))

    -- Bucket Section
    local buckets = snapshotData.buckets or {}
    if #buckets > 0 then
        for _, b in ipairs(buckets) do
            local currentPctStr = string.format("%.1f%%", b.currentPct or 0)
            local targetPctStr = string.format("%.1f%%", b.targetPct or 0)
            local driftVal = b.driftPct or 0
            local driftSign = (driftVal >= 0) and "+" or ""
            local driftStr = string.format("%s%.1f%% Δ", driftSign, driftVal)

            local valStr = ""
            if b.rawKey == "LEGACY_HOLDINGS" or b.targetPct == 0 then
                valStr = currentPctStr .. " (Legacy)"
            else
                valStr = string.format("%s (Target %s, %s)", currentPctStr, targetPctStr, driftStr)
            end

            table.insert(lines, string.format("%-20s %s", b.name, valStr))
        end
    end

    barWidget.setTooltip(table.concat(lines, "\n"))
end

function update()
    local raw = noctalia.state.get("pos.snapshot")
    if type(raw) == "string" and raw ~= "" then
        snapshotData = noctalia.json.decode(raw)
    end
    renderWidget()
end

noctalia.state.watch("pos.snapshot", function(raw)
    if type(raw) == "string" and raw ~= "" then
        snapshotData = noctalia.json.decode(raw)
        renderWidget()
    end
end)

function onClick()
    local dashboardUrl = noctalia.getConfig("dashboard_url")
    if type(dashboardUrl) == "string" and dashboardUrl ~= "" then
        local quoted = "'" .. dashboardUrl:gsub("'", "'\\''") .. "'"
        local cmd = "gio open " .. quoted .. " || xdg-open " .. quoted
        noctalia.runAsync(cmd, function(result)
            if result.exitCode ~= 0 then
                noctalia.notifyError("Portfolio OS", "Could not open dashboard URL")
            end
        end, 10000)
    end
end

function onRightClick()
    noctalia.state.set("pos.cmd", noctalia.json.encode({ action = "refresh" }))
end
