package com.kingzcheung.xime.ui.keyboard.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutModelsTest {
    @Test
    fun builtInSpecs_areValid() {
        KeyboardLayoutPresets.all.forEach { preset ->
            assertTrue("invalid preset: ${preset.id}", KeyboardLayoutValidator.validate(preset))
        }
    }

    @Test
    fun validator_rejectsDuplicateItemIds() {
        val spec = KeyboardLayoutSpec(
            id = "duplicate",
            name = "duplicate",
            widthUnits = 10f,
            heightUnits = 1f,
            items = listOf(
                KeyboardLayoutItem("key", 0f, 0f, 1f, content = KeyboardItemContent.Character("a")),
                KeyboardLayoutItem("key", 1f, 0f, 1f, content = KeyboardItemContent.Character("b")),
            ),
        )
        assertFalse(KeyboardLayoutValidator.validate(spec))
    }

    @Test
    fun validator_rejectsOutOfBoundsAndNonFiniteGeometry() {
        val outOfBounds = KeyboardLayoutSpec(
            id = "out",
            name = "out",
            widthUnits = 10f,
            heightUnits = 1f,
            items = listOf(
                KeyboardLayoutItem("key", 9.5f, 0f, 1f, content = KeyboardItemContent.Character("a")),
            ),
        )
        val nonFinite = KeyboardLayoutSpec(
            id = "nan",
            name = "nan",
            widthUnits = Float.NaN,
            heightUnits = 1f,
            items = emptyList(),
        )
        assertFalse(KeyboardLayoutValidator.validate(outOfBounds))
        assertFalse(KeyboardLayoutValidator.validate(nonFinite))
    }

    @Test
    fun gboard_usesExpectedTenUnitGeometry() {
        val spec = KeyboardLayoutPresets.gboard
        assertTrue(spec.widthUnits == 10f)
        val shift = spec.items.first { it.id == "shift" }
        val backspace = spec.items.first { it.id == "backspace" }
        val space = spec.items.first { it.id == "space" }
        assertTrue(shift.width == 1.5f)
        assertTrue(backspace.width == 1.5f)
        assertTrue(space.width == 4f)
    }
}
