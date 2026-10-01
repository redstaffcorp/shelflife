package hu.rsc.shelflife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLifeDefaultsTest {

    @Test
    fun homeLocationEstimates_matchPreviousQuickGridValues() {
        // Hely valasztasa nelkul a gyorsracs pontosan ugyanazt adja, mint korabban.
        val expected = mapOf(
            FoodCategory.BREAD to 3, FoodCategory.PASTRY to 2, FoodCategory.EGGS to 21,
            FoodCategory.MEAT to 2, FoodCategory.POULTRY to 2, FoodCategory.FISH to 1,
            FoodCategory.COLD_CUTS to 5, FoodCategory.CHEESE_DELI to 10,
            FoodCategory.VEGETABLES to 5, FoodCategory.SALAD to 3,
            FoodCategory.FRUIT to 5, FoodCategory.LEFTOVERS to 3
        )
        assertEquals(FoodCategory.entries.toSet(), expected.keys)
        expected.forEach { (cat, days) -> assertEquals(cat.name, days, cat.estimateDays()) }
    }

    @Test
    fun explicitHomeLocation_sameAsDefault() {
        FoodCategory.entries.forEach { assertEquals(it.name, it.estimateDays(), it.estimateDays(it.homeLocation)) }
    }

    @Test
    fun freezerNeverShorterThanFridge() {
        FoodCategory.entries.forEach {
            assertTrue(it.name, it.estimateDays(StorageLocation.FREEZER) >= it.estimateDays(StorageLocation.FRIDGE))
        }
    }

    @Test
    fun perishablesInPantry_expireToday() {
        listOf(FoodCategory.MEAT, FoodCategory.POULTRY, FoodCategory.FISH, FoodCategory.LEFTOVERS).forEach {
            assertEquals(it.name, 0, it.estimateDays(StorageLocation.PANTRY))
        }
    }

    @Test
    fun noNegativeEstimates() {
        FoodCategory.entries.forEach { cat ->
            StorageLocation.entries.forEach { loc -> assertTrue("$cat/$loc", cat.estimateDays(loc) >= 0) }
        }
    }

    @Test
    fun laterEstimate_dependsOnLocation() {
        assertEquals(7, ShelfLifeDefaults.laterEstimateDays(StorageLocation.FRIDGE))
        assertEquals(7, ShelfLifeDefaults.laterEstimateDays(null))
        assertEquals(14, ShelfLifeDefaults.laterEstimateDays(StorageLocation.PANTRY))
        assertEquals(60, ShelfLifeDefaults.laterEstimateDays(StorageLocation.FREEZER))
    }
}
