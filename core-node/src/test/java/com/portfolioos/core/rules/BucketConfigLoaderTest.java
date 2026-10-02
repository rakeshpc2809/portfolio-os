package com.portfolioos.core.rules;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BucketConfigLoaderTest {

    @Test
    void testIsPreferredFund() {
        assertTrue(BucketConfigLoader.isPreferredFund("NIFTY_LARGEMIDCAP"));
        assertTrue(BucketConfigLoader.isPreferredFund("PPFAS_FLEXICAP"));
        assertTrue(BucketConfigLoader.isPreferredFund("VALUE_30"));
        assertTrue(BucketConfigLoader.isPreferredFund("MOMENTUM_50"));
        assertTrue(BucketConfigLoader.isPreferredFund("SMALL_CAP"));
        assertTrue(BucketConfigLoader.isPreferredFund("GOLD_PASSIVE"));
        assertTrue(BucketConfigLoader.isPreferredFund("ARBITRAGE_FUND"));
        assertFalse(BucketConfigLoader.isPreferredFund("UNKNOWN_RANDOM_FUND"));
        assertFalse(BucketConfigLoader.isPreferredFund(null));
    }

    @Test
    void testGetPreferredBucketForAsset() {
        assertEquals("EQUITY_CORE", BucketConfigLoader.getPreferredBucketForAsset("NIFTY_LARGEMIDCAP_1", "Large and Midcap Index Fund"));
        assertNull(BucketConfigLoader.getPreferredBucketForAsset(null, null));
    }

    @Test
    void testMapAssetToBucket() {
        assertNotNull(BucketConfigLoader.mapAssetToBucket("INF109KC13X2", "ICICI Nifty 200"));
        assertEquals("LEGACY_HOLDINGS", BucketConfigLoader.mapAssetToBucket("INF109K01234", "Nifty 100 Equal Weight Index Fund"));
    }

    @Test
    void testGetActiveVersion() {
        BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(LocalDate.now());
        assertNotNull(activeVersion);
        assertNotNull(activeVersion.targets());
        assertFalse(activeVersion.targets().isEmpty());
    }

    @Test
    void testNoFundAppearsInMultipleBucketsInYaml() {
        BucketConfigLoader.BucketRulesConfig rulesConfig = BucketConfigLoader.loadConfig();
        assertNotNull(rulesConfig);
        assertNotNull(rulesConfig.versions());

        for (BucketConfigLoader.BucketTargetVersion version : rulesConfig.versions()) {
            java.util.Map<String, String> isinToBucketMap = new java.util.HashMap<>();
            for (BucketConfigLoader.BucketTargetConfig target : version.targets()) {
                if (target.preferredFunds() != null) {
                    for (BucketConfigLoader.PreferredFundConfig fund : target.preferredFunds()) {
                        String isin = fund.fundId();
                        assertNotNull(isin, "Preferred fund ISIN cannot be null in version " + version.versionId());
                        if (isinToBucketMap.containsKey(isin)) {
                            fail("DUPLICATE BUCKET MAPPING ERROR: ISIN " + isin +
                                 " appears under both bucket '" + isinToBucketMap.get(isin) +
                                 "' and bucket '" + target.bucket() + "' in YAML version " + version.versionId());
                        }
                        isinToBucketMap.put(isin, target.bucket());
                    }
                }
            }
        }
    }

    @Test
    void testSchemaVersionParsingCompatibility() {
        BucketConfigLoader.BucketRulesConfig config = BucketConfigLoader.loadConfig();
        assertNotNull(config, "Config loader must successfully return BucketRulesConfig");
        assertFalse(config.versions().isEmpty(), "Versions list must contain active v2.3 config version");

        BucketConfigLoader.BucketTargetVersion activeVer = config.versions().get(0);
        assertNotNull(activeVer.versionId(), "Version ID must be present");
        assertNotNull(activeVer.targets(), "Bucket targets list must not be null");
        assertTrue(activeVer.targets().size() >= 4, "Config targets list must have at least 4 defined bucket targets");
    }

    @Test
    void testLegacyLiquidationCandidatesParsingAndSafetyInvariants() {
        BucketConfigLoader.resetCache();
        List<BucketConfigLoader.LegacyLiquidationCandidate> candidates = BucketConfigLoader.getLegacyLiquidationCandidates();
        assertNotNull(candidates, "Legacy liquidation candidates must not be null");
        assertEquals(4, candidates.size(), "Must parse exactly 4 verified legacy candidates");

        // Verify candidate details
        var kotak = candidates.stream().filter(c -> c.isin().equals("INF174KA1TY2")).findFirst().orElseThrow();
        assertEquals("Kotak Nifty 100 Equal Weight Index Fund Direct Growth", kotak.fundName());
        assertTrue(kotak.reason().contains("systematic tax-free LTCG liquidation"));

        var midcap = candidates.stream().filter(c -> c.isin().equals("INF247L01916")).findFirst().orElseThrow();
        assertEquals("Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth", midcap.fundName());

        var microcap = candidates.stream().filter(c -> c.isin().equals("INF247L01BQ9")).findFirst().orElseThrow();
        assertEquals("Motilal Oswal Nifty Microcap 250 Index Fund Direct Growth", microcap.fundName());

        var healthcare = candidates.stream().filter(c -> c.isin().equals("INF769K01ED6")).findFirst().orElseThrow();
        assertEquals("Mirae Asset Healthcare Fund Direct Growth", healthcare.fundName());
    }
}
