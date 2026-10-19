package com.example.blindobstacledetection.navigation.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalPlaceNormalizerTest {

    @Test
    fun testNormalizeNilamburToNeelambur() {
        assertEquals("Neelambur", RegionalPlaceNormalizer.normalize("nilambur"))
        assertEquals("Neelambur", RegionalPlaceNormalizer.normalize("Nilambur"))
        assertEquals("Neelambur", RegionalPlaceNormalizer.normalize("NILAMBUR"))
        assertEquals("Neelambur", RegionalPlaceNormalizer.normalize("neelamboor"))
        assertEquals("Neelambur", RegionalPlaceNormalizer.normalize("nilamboor"))
    }

    @Test
    fun testNormalizeOtherRegionalAliases() {
        assertEquals("Peelamedu", RegionalPlaceNormalizer.normalize("pilamedu"))
        assertEquals("Thudiyalur", RegionalPlaceNormalizer.normalize("thudiyaloor"))
        assertEquals("Singanallur", RegionalPlaceNormalizer.normalize("singanaloor"))
        assertEquals("Gandhipuram", RegionalPlaceNormalizer.normalize("gaandhipuram"))
        assertEquals("Saravanampatti", RegionalPlaceNormalizer.normalize("saravanampatty"))
        assertEquals("Ganapathy", RegionalPlaceNormalizer.normalize("ganapathi"))
        assertEquals("Ukkadam", RegionalPlaceNormalizer.normalize("ukkadom"))
        assertEquals("Town Hall", RegionalPlaceNormalizer.normalize("townhall"))
        assertEquals("RS Puram", RegionalPlaceNormalizer.normalize("rs puram"))
    }

    @Test
    fun testMultiWordNormalization() {
        assertEquals("Neelambur Bus Stop", RegionalPlaceNormalizer.normalize("Nilambur Bus Stop"))
        assertEquals("Peelamedu Railway Station", RegionalPlaceNormalizer.normalize("Pilamedu Railway Station"))
    }

    @Test
    fun testTamilDetectionAndRomanization() {
        assertTrue(RegionalPlaceNormalizer.containsTamil("நீலாம்பூர்"))
        assertTrue(RegionalPlaceNormalizer.containsTamil("காந்திபுரம்"))
        assertFalse(RegionalPlaceNormalizer.containsTamil("Neelambur"))
        assertFalse(RegionalPlaceNormalizer.containsTamil("Coimbatore"))

        assertEquals("Neelambur", RegionalPlaceNormalizer.getRomanizedName("நீலாம்பூர்"))
        assertEquals("Gandhipuram", RegionalPlaceNormalizer.getRomanizedName("காந்திபுரம்"))
        assertEquals("Peelamedu", RegionalPlaceNormalizer.getRomanizedName("பீளமேடு"))
        assertEquals("Singanallur", RegionalPlaceNormalizer.getRomanizedName("சிங்காநல்லூர்"))
    }
}
