package com.example.blindobstacledetection.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationParserTest {

    @Test
    fun testNavigateToPrefix() {
        val input = "Navigate to Gandhipuram Bus Stand"
        val result = DestinationParser.parse(input)
        assertEquals("Gandhipuram Bus Stand", result)
    }

    @Test
    fun testNavigateToWithTrailingPunctuation() {
        val input = "Navigate to Gandhipuram Bus Stand."
        val result = DestinationParser.parse(input)
        assertEquals("Gandhipuram Bus Stand", result)
    }

    @Test
    fun testTakeMeToPrefix() {
        val input = "Take me to Coimbatore Railway Station"
        val result = DestinationParser.parse(input)
        assertEquals("Coimbatore Railway Station", result)
    }

    @Test
    fun testIWantToGoToPrefix() {
        val input = "I want to go to the nearest hospital"
        val result = DestinationParser.parse(input)
        assertEquals("the nearest hospital", result)
    }

    @Test
    fun testEmptyInput() {
        assertNull(DestinationParser.parse(""))
        assertNull(DestinationParser.parse("   "))
        assertNull(DestinationParser.parse(null))
    }

    @Test
    fun testCommandPrefixOnly() {
        assertNull(DestinationParser.parse("Navigate to"))
        assertNull(DestinationParser.parse("navigate to."))
        assertNull(DestinationParser.parse("Take me to"))
        assertNull(DestinationParser.parse("take me to   "))
        assertNull(DestinationParser.parse("I want to go to"))
        assertNull(DestinationParser.parse("Go to"))
    }

    @Test
    fun testPlaceNameWithoutPrefixPreserved() {
        val input = "Gandhipuram Bus Stand"
        val result = DestinationParser.parse(input)
        assertEquals("Gandhipuram Bus Stand", result)
    }

    @Test
    fun testCaseInsensitivePrefix() {
        val input = "NAVIGATE TO Coimbatore Airport"
        val result = DestinationParser.parse(input)
        assertEquals("Coimbatore Airport", result)
    }

    @Test
    fun testAdditionalNaturalPhrases() {
        assertEquals("Railway Junction", DestinationParser.parse("Please take me to Railway Junction"))
        assertEquals("Apollo Pharmacy", DestinationParser.parse("Can you navigate to Apollo Pharmacy"))
        assertEquals("City Center", DestinationParser.parse("Directions to City Center"))
        assertEquals("Town Hall", DestinationParser.parse("Go to Town Hall"))
    }

    @Test
    fun testPhoneticAliasNormalization() {
        // Specifically tests the user's issue: "Nilambur" interpreted instead of "Neelambur"
        assertEquals("Neelambur", DestinationParser.parse("Nilambur"))
        assertEquals("Neelambur", DestinationParser.parse("nilambur"))
        assertEquals("Neelambur", DestinationParser.parse("Navigate to Nilambur"))
        assertEquals("Neelambur", DestinationParser.parse("Take me to nilambur"))
        assertEquals("Peelamedu", DestinationParser.parse("Pilamedu"))
        assertEquals("Thudiyalur", DestinationParser.parse("thudiyaloor"))
        assertEquals("Singanallur", DestinationParser.parse("singanaloor"))
        assertEquals("Gandhipuram", DestinationParser.parse("gaandhipuram"))
    }

    @Test
    fun testTamilVoiceCommandsAndDativeInflections() {
        // Spoken Tamil destination commands
        assertEquals("நீலாம்பூர்", DestinationParser.parse("நீலாம்பூர்"))
        assertEquals("நீலாம்பூர்", DestinationParser.parse("நீலாம்பூர் போ"))
        assertEquals("நீலாம்பூர்", DestinationParser.parse("நீலாம்பூருக்கு போ"))
        assertEquals("நீலாம்பூர்", DestinationParser.parse("நீலாம்பூருக்கு வழி காட்டு"))
        assertEquals("நீலாம்பூர்", DestinationParser.parse("வழி காட்டு நீலாம்பூர்"))
        assertEquals("காந்திபுரம்", DestinationParser.parse("காந்திபுரத்திற்கு போ"))
        assertEquals("காந்திபுரம்", DestinationParser.parse("காந்திபுரம் போ"))
        assertEquals("மருத்துவமனை", DestinationParser.parse("மருத்துவமனைக்கு போ"))
        assertEquals("பேருந்து நிலையம்", DestinationParser.parse("பேருந்து நிலையத்திற்கு போ"))
    }
}
