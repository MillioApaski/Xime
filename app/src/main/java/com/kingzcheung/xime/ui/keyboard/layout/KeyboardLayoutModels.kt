package com.kingzcheung.xime.ui.keyboard.layout

/**
 * 可序列化布局模型的第一版。当前内置 preset 由代码构造，后续自定义编辑器可直接复用这一模型。
 * 坐标统一使用逻辑 unit，避免把 dp 写进配置。
 */
data class KeyboardLayoutSpec(
    val version: Int = 1,
    val id: String,
    val name: String,
    val widthUnits: Float,
    val heightUnits: Float,
    val items: List<KeyboardLayoutItem>,
)

data class KeyboardLayoutItem(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float = 1f,
    val content: KeyboardItemContent,
    val editable: Boolean = true,
)

sealed interface KeyboardItemContent {
    data class CharacterRow(
        val sourceRow: Int,
        val sizing: CharacterRowSizing = CharacterRowSizing.FILL,
    ) : KeyboardItemContent

    data class Character(val value: String) : KeyboardItemContent
    data class SpecialKey(val role: SpecialKeyRole) : KeyboardItemContent
    data object Empty : KeyboardItemContent
}

enum class CharacterRowSizing {
    FILL,
    CENTER_UNIT_KEYS,
    FIXED,
}

enum class SpecialKeyRole {
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    MODE_CHANGE,
    LANGUAGE,
    EMOJI,
    COMMA,
    PERIOD,
    ESC,
    TAB,
    CAPS_LOCK,
    CTRL,
    ALT,
    META,
    FN,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    INSERT,
    FORWARD_DELETE,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    PRINT_SCREEN,
    SCROLL_LOCK,
    PAUSE,
    NUM_LOCK,
    NUMPAD_0,
    NUMPAD_1,
    NUMPAD_2,
    NUMPAD_3,
    NUMPAD_4,
    NUMPAD_5,
    NUMPAD_6,
    NUMPAD_7,
    NUMPAD_8,
    NUMPAD_9,
    NUMPAD_DECIMAL,
    NUMPAD_ADD,
    NUMPAD_SUBTRACT,
    NUMPAD_MULTIPLY,
    NUMPAD_DIVIDE,
    NUMPAD_ENTER,
}

object KeyboardLayoutValidator {
    fun validate(spec: KeyboardLayoutSpec): Boolean {
        if (spec.version <= 0 || !spec.widthUnits.isFinite() || !spec.heightUnits.isFinite()) return false
        if (spec.widthUnits <= 0f || spec.heightUnits <= 0f) return false
        if (spec.items.map { it.id }.toSet().size != spec.items.size) return false
        return spec.items.all { item ->
            item.x.isFinite() && item.y.isFinite() && item.width.isFinite() && item.height.isFinite() &&
                item.x >= 0f && item.y >= 0f && item.width > 0f && item.height > 0f &&
                item.x + item.width <= spec.widthUnits + 0.001f &&
                item.y + item.height <= spec.heightUnits + 0.001f
        }
    }
}
