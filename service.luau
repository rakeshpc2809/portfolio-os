local pollPending = false
local pollStartedAt = 0
local generation = 0
local STUCK_MS = 20000

local function parseFloat(val)
    if type(val) == "number" then return val end
    if type(val) ~= "string" or val == "" then return 0 end
    return tonumber(val) or 0
end

local function formatCompactCurrency(numOrStr)
    if not numOrStr or numOrStr == "N/A" or numOrStr == "" then return "N/A" end
    local str = tostring(numOrStr)
    local isNeg = false
    if str:sub(1, 1) == "-" then
        isNeg = true
        str = str:sub(2)
    end
    local cleaned = (str:gsub("[^0-9%.]", ""))
    local num = tonumber(cleaned)
    if not num then return tostring(numOrStr) end
    
    local prefix = isNeg and "-₹" or "₹"
    if num >= 10000000 then
        return string.format("%s%.2fCr", prefix, num / 10000000)
    elseif num >= 100000 then
        return string.format("%s%.2fL", prefix, num / 100000)
    elseif num >= 1000 then
        return string.format("%s%.1fk", prefix, num / 1000)
    else
        return string.format("%s%.0f", prefix, num)
    end
end

local function cleanBucketName(raw)
    if not raw or raw == "" then return "Unknown" end
    if raw == "EQUITY_CORE" then return "Equity Core" end
    if raw == "EQUITY_SATELLITE" then return "Equity Satellite" end
    if raw == "GOLD_SILVER" then return "Gold & Silver" end
    if raw == "LIQUID_BUFFER" then return "Liquid Buffer" end
    if raw == "LEGACY_HOLDINGS" then return "Legacy Holdings" end
    return raw:gsub("_", " "):lower():gsub("(%a)([%w_']*)", function(first, rest)
        return first:upper() .. rest
    end)
end

local function poll()
    local now = noctalia.nowMs()
    if pollPending then
        if now - pollStartedAt < STUCK_MS then
            return
        end
        pollPending = false
    end

    pollPending = true
    pollStartedAt = now
    generation = generation + 1
    local currentGen = generation

    local pending = 2
    local portSummaryRes, bucketRes = nil, nil
    local portError, bucketError = nil, nil

    local function onComplete()
        if currentGen ~= generation then return end
        pending = pending - 1
        if pending == 0 then
            pollPending = false
            if portSummaryRes == nil and bucketRes == nil then
                local errText = portError or bucketError or "Request failed"
                local errPayload = {
                    ok = false,
                    error = errText,
                    checkedAt = noctalia.formatTime("%H:%M:%S")
                }
                noctalia.state.set("pos.snapshot", noctalia.json.encode(errPayload))
            else
                local rawNetWorth = portSummaryRes and (portSummaryRes.total_current_value or portSummaryRes.totalCurrentValue) or "0"
                local xirr = portSummaryRes and (portSummaryRes.xirr_percentage or portSummaryRes.xirrPercentage) or "N/A"

                local rawBucketStatuses = bucketRes and (bucketRes.bucket_statuses or bucketRes.bucketStatuses) or {}
                local processedBuckets = {}

                for _, b in ipairs(rawBucketStatuses) do
                    local name = cleanBucketName(b.bucket or b.bucket_name or b.bucketName)
                    local currentPct = parseFloat(b.current_pct or b.currentPct)
                    local targetPct = parseFloat(b.target_pct or b.targetPct)
                    local driftPct = parseFloat(b.drift_pct or b.driftPct)
                    local isDrifted = b.is_drifted ~= nil and b.is_drifted or (b.isDrifted ~= nil and b.isDrifted or false)

                    table.insert(processedBuckets, {
                        name = name,
                        rawKey = b.bucket or "",
                        currentPct = currentPct,
                        targetPct = targetPct,
                        driftPct = driftPct,
                        isDrifted = isDrifted
                    })
                end

                local snapshot = {
                    ok = true,
                    total_net_worth_compact = formatCompactCurrency(rawNetWorth),
                    xirr = xirr,
                    buckets = processedBuckets,
                    checkedAt = noctalia.formatTime("%H:%M:%S")
                }
                noctalia.state.set("pos.snapshot", noctalia.json.encode(snapshot))
            end
        end
    end

    local baseUrl = noctalia.getConfig("base_url") or "http://localhost:8080"
    local authName = noctalia.getConfig("auth_header_name") or ""
    local authVal = noctalia.getConfig("auth_header_value") or ""
    local headers = nil
    if authName ~= "" and authVal ~= "" then
        headers = { authName .. ": " .. authVal }
    end

    -- 1. Fetch Portfolio Summary (Net Worth & XIRR)
    local portStarted = noctalia.http({ url = baseUrl .. "/api/v1/reports/summary", method = "GET", headers = headers }, function(res)
        if not res.ok then
            portError = "Portfolio summary unreachable"
        elseif res.status ~= 200 then
            portError = "Portfolio HTTP " .. tostring(res.status)
        else
            portSummaryRes = noctalia.json.decode(res.body)
        end
        onComplete()
    end)
    if not portStarted then
        portError = "Portfolio request failed to start"
        onComplete()
    end

    -- 2. Fetch Bucket Allocations & Drift Status
    local bucketStarted = noctalia.http({ url = baseUrl .. "/api/v1/rebalance/bucket", method = "GET", headers = headers }, function(res)
        if not res.ok then
            bucketError = "Bucket rebalance unreachable"
        elseif res.status ~= 200 then
            bucketError = "Bucket HTTP " .. tostring(res.status)
        else
            bucketRes = noctalia.json.decode(res.body)
        end
        onComplete()
    end)
    if not bucketStarted then
        bucketError = "Bucket request failed to start"
        onComplete()
    end
end

function update()
    local interval = noctalia.getConfig("poll_interval") or 60
    noctalia.setUpdateInterval(interval * 1000)
    poll()
end

function onConfigChanged()
    update()
end

noctalia.state.watch("pos.cmd", function(raw)
    if type(raw) ~= "string" or raw == "" then return end
    local cmd = noctalia.json.decode(raw)
    if type(cmd) == "table" and cmd.action == "refresh" then
        poll()
    end
end)
