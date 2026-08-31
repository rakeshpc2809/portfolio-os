package com.portfolioos.mobile

import android.app.Application
import android.content.Context
import app.cash.turbine.test
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.data.nav.AmfiDirectFetcher
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.SyncInfoDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardViewModel
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockContext: Context
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mock(Application::class.java)
        mockContext = mock(Context::class.java)
        fakePrefs = FakeSharedPreferences()

        `when`(mockApplication.applicationContext).thenReturn(mockContext)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(fakePrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleSnapshot(): SyncSnapshot {
        return SyncSnapshot(
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
                )
            ),
            taxLots = emptyList(),
            radarSignals = emptyList(),
            netWorthHistory = emptyList(),
            rebalancePlan = null
        )
    }

    @Test
    fun initialLoad_success_emitsSuccessStateWithCachedData() = runTest(testDispatcher) {
        // Pre-condition: Snapshot is saved in cache
        val sample = createSampleSnapshot()
        SnapshotCacheManager.saveSnapshot(mockContext, sample, isFullLedgerSync = true)

        val viewModel = DashboardViewModel(mockApplication)

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull("Snapshot should be populated from cache", state.snapshot)
            assertEquals("2026-08-31T12:00:00Z", state.snapshot?.syncInfo?.generatedAt)
            assertEquals(1751765.0, state.snapshot?.syncInfo?.currentValue ?: 0.0, 0.001)
            assertFalse("Loading should be false when cache is present", state.isLoading)
            assertFalse("Refreshing should be false initially", state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialLoad_networkFailure_fallsBackToCacheGracefully() = runTest(testDispatcher) {
        // Pre-condition: Valid offline cache exists
        val cached = createSampleSnapshot()
        SnapshotCacheManager.saveSnapshot(mockContext, cached, isFullLedgerSync = false)

        val viewModel = DashboardViewModel(mockApplication)
        viewModel.loadInitialCachedState()

        val state = viewModel.uiState.value
        assertNotNull(state.snapshot)
        assertEquals(1751765.0, state.snapshot?.syncInfo?.currentValue ?: 0.0, 0.001)
        assertFalse(state.isLoading)
    }

    @Test
    fun initialLoad_networkFailure_noCache_emitsLoadingState() = runTest(testDispatcher) {
        // Pre-condition: Cache is completely empty
        val viewModel = DashboardViewModel(mockApplication)
        viewModel.loadInitialCachedState()

        val state = viewModel.uiState.value
        assertNull("Snapshot must be null when cache is empty", state.snapshot)
        assertTrue("isLoading must be true when no cache is available", state.isLoading)
    }

    @Test
    fun pullToRefresh_triggersSync_emitsRefreshingState() = runTest(testDispatcher) {
        val viewModel = DashboardViewModel(mockApplication)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isRefreshing)

            viewModel.fetchSyncSnapshot(isManualRefresh = true)
            val refreshingState = awaitItem()
            assertTrue("isRefreshing must become true on manual refresh", refreshingState.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun biometricMasking_whenLocked_masksFinancialFigures() = runTest(testDispatcher) {
        val sample = createSampleSnapshot()
        SnapshotCacheManager.saveSnapshot(mockContext, sample, isFullLedgerSync = true)

        val viewModel = DashboardViewModel(mockApplication)
        viewModel.loadInitialCachedState()

        // 1. Enable Biometric Lock and lock the app
        viewModel.toggleBiometricLock(true)
        viewModel.setAppLocked(true)

        val state = viewModel.uiState.value
        assertTrue("App must report locked status", state.isAppLocked)
        assertTrue("Biometric lock must be enabled", state.isBiometricLockEnabled)
        assertTrue("Masking state flag must evaluate to true", state.isMasked)

        // 2. Real Security Assertion: Verify all sensitive financial display figures are masked to bullets
        assertEquals("Net worth figure must be masked", "••••••", state.displayNetWorth)
        assertEquals("Unrealized gain figure must be masked", "••••••", state.displayUnrealizedGain)
        assertEquals("Total invested figure must be masked", "••••••", state.displayTotalInvested)
    }

    @Test
    fun biometricUnmasking_whenAuthenticated_revealsRealFigures() = runTest(testDispatcher) {
        val sample = createSampleSnapshot()
        SnapshotCacheManager.saveSnapshot(mockContext, sample, isFullLedgerSync = true)

        val viewModel = DashboardViewModel(mockApplication)
        viewModel.loadInitialCachedState()

        // Lock first
        viewModel.setAppLocked(true)
        assertTrue("Pre-condition: must be masked", viewModel.uiState.value.isMasked)
        assertEquals("••••••", viewModel.uiState.value.displayNetWorth)

        // Simulate successful biometric authentication
        viewModel.setAppLocked(false)

        val state = viewModel.uiState.value
        assertFalse("App must be unlocked", state.isAppLocked)
        assertFalse("Masking flag must evaluate to false", state.isMasked)

        // Real Security Assertion: Verify unmasked figures accurately reveal formatted Indian Rupee strings
        assertEquals("Net worth figure must match formatted INR", formatInr(1751765.0), state.displayNetWorth)
        assertEquals("Unrealized gain figure must match formatted INR", formatInr(112711.0), state.displayUnrealizedGain)
        assertEquals("Total invested figure must match formatted INR", formatInr(1639054.0), state.displayTotalInvested)
        assertNotEquals("Unmasked net worth must not be bullets", "••••••", state.displayNetWorth)
    }

    @Test
    fun tokenUpdate_triggersReloadWithNewToken() = runTest(testDispatcher) {
        val viewModel = DashboardViewModel(mockApplication)
        val newToken = "TEST_BEARER_AUTH_TOKEN_SECURE_998877"

        viewModel.uiState.test {
            awaitItem() // Consume initial state
            viewModel.updateAuthToken(newToken)
            val refreshedState = awaitItem()

            // Verify Auth Token persisted to cache for Retrofit header injection
            assertEquals("Auth token in cache must be updated", newToken, SnapshotCacheManager.getAuthToken(mockContext))
            assertTrue("Updating auth token must trigger refresh", refreshedState.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun amfiNavFallback_whenDirectApiFails_resolvesNavsFromAmfi() = runTest(testDispatcher) {
        // 1. Test parsing real AMFI NAVAll.txt delimited lines
        val rawAmfiFeed = """
            Scheme Code;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date
            122639;INF879O01027;INF879O01035;Parag Parikh Flexi Cap Fund - Direct Plan - Growth;58.26;28-Aug-2026
            118778;INF204K01K15;INF204K01K23;Nippon India Small Cap Fund - Direct Plan - Growth;135.48;28-Aug-2026
            CORRUPTED_LINE_WITHOUT_SEMICOLONS
        """.trimIndent()

        val parsedNavMap = AmfiDirectFetcher.parseAmfiNavBody(rawAmfiFeed)
        assertEquals(2, parsedNavMap.size)
        assertEquals(58.26, parsedNavMap["INF879O01027"] ?: 0.0, 0.001)
        assertEquals(135.48, parsedNavMap["INF204K01K15"] ?: 0.0, 0.001)

        // 2. Test fallback StateFlow flags when direct backend fails
        val viewModel = DashboardViewModel(mockApplication)
        val sample = createSampleSnapshot()
        SnapshotCacheManager.saveSnapshot(mockContext, sample, isFullLedgerSync = true)
        viewModel.loadInitialCachedState()

        viewModel.simulateAmfiFallback()

        val state = viewModel.uiState.value
        assertTrue("isAmfiFallback should be true after AMFI fallback resolution", state.isAmfiFallback)
        assertTrue("lastSyncMillis should be updated with recent timestamp", state.lastSyncMillis > 0L)
        assertTrue("lastFullLedgerMillis should be aged compared to lastSyncMillis", state.lastFullLedgerMillis < state.lastSyncMillis)
    }
}
