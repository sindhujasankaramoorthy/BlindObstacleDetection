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
}
