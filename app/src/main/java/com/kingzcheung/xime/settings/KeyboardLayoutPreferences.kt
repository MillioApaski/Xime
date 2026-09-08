package com.kingzcheung.xime.settings

import android.content.Context

enum class KeyboardLayoutPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val recommendedMinWidthDp: Int? = null,
) {
    XIME_DEFAULT("xime_default", "Xime 默认", "保留当前竖屏布局"),
    XIME_SPLIT("xime_split", "Xime 分体", "保留当前横屏分体布局"),
    GBOARD("gboard", "Gboard", "10 单位网格、数字行与半键错位"),
    PC68("pc68", "PC 68", "适合平板横屏的紧凑 PC 布局", 700),
    PC_TKL("pc_tkl", "PC TKL（87/88）", "带 F 区、编辑区和方向键", 900),
    PC104("pc104", "PC 104", "完整主键区、编辑区和数字小键盘", 1100),
    ;

    companion object {
        fun fromId(id: String?, fallback: KeyboardLayoutPreset): KeyboardLayoutPreset =
            entries.firstOrNull { it.id == id } ?: fallback
    }
}

/**
 * 键盘几何布局的 App 级偏好。字符本身仍由 Rime / KeysConfigHelper 提供，
 * 这里仅保存“用哪种几何布局”和少量可视化布局参数，避免出现两套字符 source of truth。
 */
object KeyboardLayoutPreferences {
    const val KEY_PORTRAIT_PRESET = "keyboard_layout_portrait"
    const val KEY_LANDSCAPE_PRESET = "keyboard_layout_landscape"
    const val KEY_GBOARD_NUMBER_ROW = "keyboard_layout_gboard_number_row"
    const val KEY_BOTTOM_ROW_ORDER = "keyboard_layout_bottom_row_order"
    const val KEY_SCHEMA_VERSION = "keyboard_layout_schema_version"

    const val SCHEMA_VERSION = 1

    val defaultBottomRowOrder = listOf(
        "mode",
        "comma",
        "language",
        "space",
        "period",
        "enter",
    )

    private val validBottomItems = defaultBottomRowOrder.toSet()

    fun getPortraitPreset(context: Context): KeyboardLayoutPreset =
        KeyboardLayoutPreset.fromId(
            SettingsPreferences.getPrefsPublic(context).getString(KEY_PORTRAIT_PRESET, null),
            KeyboardLayoutPreset.XIME_DEFAULT,
        )

    fun setPortraitPreset(context: Context, preset: KeyboardLayoutPreset) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_PORTRAIT_PRESET, preset.id)
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .apply()
    }

    fun getLandscapePreset(context: Context): KeyboardLayoutPreset =
        KeyboardLayoutPreset.fromId(
            SettingsPreferences.getPrefsPublic(context).getString(KEY_LANDSCAPE_PRESET, null),
            KeyboardLayoutPreset.XIME_SPLIT,
        )

    fun setLandscapePreset(context: Context, preset: KeyboardLayoutPreset) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_LANDSCAPE_PRESET, preset.id)
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .apply()
    }

    fun isGboardNumberRowEnabled(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_GBOARD_NUMBER_ROW, true)

    fun setGboardNumberRowEnabled(context: Context, enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putBoolean(KEY_GBOARD_NUMBER_ROW, enabled)
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .apply()
    }

    fun getBottomRowOrder(context: Context): List<String> {
        val raw = SettingsPreferences.getPrefsPublic(context)
            .getString(KEY_BOTTOM_ROW_ORDER, null)
            ?: return defaultBottomRowOrder
        val parsed = raw.split(',')
            .map { it.trim() }
            .filter { it in validBottomItems }
            .distinct()
        return if (parsed.size == validBottomItems.size && parsed.toSet() == validBottomItems) {
            parsed
        } else {
            defaultBottomRowOrder
        }
    }

    fun setBottomRowOrder(context: Context, order: List<String>) {
        val normalized = order
            .filter { it in validBottomItems }
            .distinct()
        if (normalized.size != validBottomItems.size || normalized.toSet() != validBottomItems) return
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_BOTTOM_ROW_ORDER, normalized.joinToString(","))
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .apply()
    }

    fun resetGeometry(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .remove(KEY_PORTRAIT_PRESET)
            .remove(KEY_LANDSCAPE_PRESET)
            .remove(KEY_GBOARD_NUMBER_ROW)
            .remove(KEY_BOTTOM_ROW_ORDER)
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .apply()
    }
}
