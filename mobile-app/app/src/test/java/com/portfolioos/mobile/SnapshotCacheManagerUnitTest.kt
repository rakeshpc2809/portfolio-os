package com.portfolioos.mobile

import android.content.Context
import android.content.SharedPreferences
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class SnapshotCacheManagerUnitTest {

    private lateinit var mockContext: Context
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        fakePrefs = FakeSharedPreferences()
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(fakePrefs)
    }

    @Test
    fun saveAndReadSnapshot_roundTripIntegrity() {
        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(
                timestamp = 1788000000000L,
                generatedAt = "2026-08-31T12:00:00Z",
                fiscalYear = "2026-27",
                totalInvested = 1639054.0,
                currentValue = 1751765.0,
                unrealizedGain = 112711.0,
                xirrPercentage = "+8.23%"
            ),
            holdings = listOf(
                FlatHoldingDto(
                    fundName = "Parag Parikh Flexi Cap Fund",
                    isin = "INF879O01027",
                    assetBucket = "Core",
                    currentValue = 725400.0,
                    investedValue = 680000.0,
                    totalUnits = 12450.5,
                    avgCost = 54.6,
                    xirr = 9.45
                ),
                FlatHoldingDto(
                    fundName = "Nippon India Small Cap Fund",
                    isin = "INF204K01K15",
                    assetBucket = "Satellite",
                    currentValue = 420000.0,
                    investedValue = 390000.0,
                    totalUnits = 3100.2,
                    avgCost = 125.8,
                    xirr = 11.2
                )
            ),
            taxLots = listOf(
                FlatTaxLotDto(
                    isin = "INF879O01027",
                    buyDate = "2024-01-15",
                    units = 500.0,
                    taxClassification = "LTCG",
                    isLongTerm = true,
                    costPerUnit = 50.0,
                    holdingDays = 400L,
                    daysToLtcg = 0L
                )
            ),
            radarSignals = listOf(
                RadarSignalDto(
                    signalType = "REBALANCE",
                    title = "Drift Alert",
                    subtitle = "Core Drift",
                    description = "Core allocation exceeded upper drift threshold",
                    severity = "WARNING"
                )
            ),
            netWorthHistory = listOf(
                NetWorthPointDto(
                    date = "2026-08-31",
                    valuation = 1751765.0,
                    invested = 1639054.0
                )
            ),
            rebalancePlan = RebalancePlanDto(
                planId = "PLAN-001",
                generatedAt = "2026-08-31T12:00:00Z",
                buySide = BuySidePlanDto(totalToInvest = 50000.0)
            )
        )

        // Save snapshot with full ledger flag
        SnapshotCacheManager.saveSnapshot(mockContext, snapshot, isFullLedgerSync = true)

        // Read snapshot back
        val loaded = SnapshotCacheManager.loadSnapshot(mockContext)
        assertNotNull("Loaded snapshot should not be null", loaded)
        assertEquals("2026-08-31T12:00:00Z", loaded?.syncInfo?.generatedAt)
        assertEquals(1751765.0, loaded?.syncInfo?.currentValue ?: 0.0, 0.001)
        assertEquals(2, loaded?.holdings?.size ?: 0)
        assertEquals("Parag Parikh Flexi Cap Fund", loaded?.holdings?.get(0)?.fundName)
        assertEquals("INF204K01K15", loaded?.holdings?.get(1)?.isin)
        assertEquals(1, loaded?.taxLots?.size ?: 0)
        assertEquals(true, loaded?.taxLots?.get(0)?.isLongTerm)
        assertEquals("PLAN-001", loaded?.rebalancePlan?.planId)
        assertEquals(50000.0, loaded?.rebalancePlan?.buySide?.totalToInvest ?: 0.0, 0.001)

        // Verify metadata flags
        assertTrue(SnapshotCacheManager.getLastSyncTimestamp(mockContext) > 0L)
        assertTrue(SnapshotCacheManager.getLastFullLedgerTimestamp(mockContext) > 0L)
        assertFalse(SnapshotCacheManager.isAmfiFallback(mockContext))
        assertFalse(SnapshotCacheManager.isFullyOffline(mockContext))
    }

    @Test
    fun readSnapshot_corruptedFile_returnsNullGracefully() {
        // Corrupted Fixture 1: Truncated JSON string (incomplete object)
        val truncatedJson = """{"sync_info":{"timestamp":1788000000000,"generated_at":"""
        fakePrefs.putStringDirect("key_snapshot_json", truncatedJson)
        val result1 = SnapshotCacheManager.loadSnapshot(mockContext)
        assertNull("Truncated JSON should return null without crashing", result1)

        // Corrupted Fixture 2: Non-JSON raw binary garbage
        val rawGarbage = """\x00\x01\xFF\xFE__MALFORMED_CORRUPTED_DISK_DATA__<<<>>>"""
        fakePrefs.putStringDirect("key_snapshot_json", rawGarbage)
        val result2 = SnapshotCacheManager.loadSnapshot(mockContext)
        assertNull("Raw binary garbage should return null without crashing", result2)

        // Corrupted Fixture 3: Incompatible schema JSON (array where object expected)
        val incompatibleSchema = """[{"unexpected_array_entry": true}]"""
        fakePrefs.putStringDirect("key_snapshot_json", incompatibleSchema)
        val result3 = SnapshotCacheManager.loadSnapshot(mockContext)
        assertNull("Incompatible JSON schema should return null without crashing", result3)
    }

    @Test
    fun defaultFallbackSnapshot_providesValidStructure() {
        val fallback = SnapshotCacheManager.createDefaultFallbackSnapshot()
        assertNotNull(fallback.syncInfo)
        assertEquals("OFFLINE_FALLBACK", fallback.syncInfo?.generatedAt)
        assertEquals("2026-27", fallback.syncInfo?.fiscalYear)
        assertTrue(fallback.holdings?.isEmpty() == true)
        assertTrue(fallback.taxLots?.isEmpty() == true)
        assertTrue(fallback.radarSignals?.isEmpty() == true)
        assertTrue(fallback.netWorthHistory?.isEmpty() == true)
        assertNull(fallback.rebalancePlan)
    }
}

/**
 * In-memory test double for Android SharedPreferences
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    fun putStringDirect(key: String, value: String?) {
        if (value == null) data.remove(key) else data[key] = value
    }

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        data[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        data[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        data[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) storage.clear()
            for ((k, v) in pending) {
                if (v == null) storage.remove(k) else storage[k] = v
            }
        }
    }
}
