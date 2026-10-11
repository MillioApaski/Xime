package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.KeyboardKeyConfig
import com.kingzcheung.xime.settings.KeyboardSpacingConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设备档键面样式档：**圆角**与**键面提示**（退格的「清空」、字母键的数字上标等）。
 *
 * 平板 26 键与各面板（数字 / 常用符号）必须给出同一档——这些值曾经分散在四处各写各的，
 * 出现过「面板圆角 8dp vs 全键盘 14dp」「面板提示 1f vs 全键盘 1.35f」这类不一致，
 * 现在全部收敛到 [keyCornerRadiusFor] / [swipeHintCornerFor] / [hintExtraScaleFor]。
 */
class KeyboardDeviceStyleTest {

    private val config = KeyboardKeyConfig(
        cornerRadius = 8,
        spacingOverrides = mapOf("symbol" to KeyboardSpacingConfig(cornerRadius = 10)),
    )

    // ── 圆角 ──────────────────────────────────────────────

    @Test
    fun `圆角未覆盖时按设备档：手机全局值、平板大键面档`() {
        assertEquals(8.dp, keyCornerRadiusFor(config, "qwerty", isTablet = false))
        assertEquals(14.dp, keyCornerRadiusFor(config, "qwerty", isTablet = true))
        // 平板值是「下限」：全局配得更大时取更大者
        assertEquals(16.dp, keyCornerRadiusFor(config.copy(cornerRadius = 16), "qwerty", isTablet = true))
    }

    @Test
    fun `圆角 yaml 按键盘覆盖优先且平板也照用覆盖值`() {
        assertEquals(10.dp, keyCornerRadiusFor(config, "symbol", isTablet = false))
        assertEquals(10.dp, keyCornerRadiusFor(config, "symbol", isTablet = true))
    }

    @Test
    fun `同一设备上圆角：面板与全键盘同值`() {
        val plain = KeyboardKeyConfig()
        for (tablet in listOf(false, true)) {
            val qwerty = keyCornerRadiusFor(plain, "qwerty", isTablet = tablet)
            for (panel in listOf("number", "symbol")) {
                assertEquals(
                    "同一设备（tablet=$tablet）上 $panel 面板必须与全键盘同一圆角",
                    qwerty, keyCornerRadiusFor(plain, panel, isTablet = tablet),
                )
            }
        }
    }

    // ── 键面提示（角标画法 / 放大倍率） ──────────────────────

    @Test
    fun `提示画到键角：手机横屏与平板一致，手机竖屏不平移`() {
        assertEquals(false, swipeHintCornerFor(isLandscape = false, isTablet = false))
        assertEquals(true, swipeHintCornerFor(isLandscape = true, isTablet = false))
        assertEquals(true, swipeHintCornerFor(isLandscape = false, isTablet = true))
        assertEquals(true, swipeHintCornerFor(isLandscape = true, isTablet = true))
    }

    @Test
    fun `提示放大倍率：仅平板放大，其余 1f`() {
        assertEquals(1f, hintExtraScaleFor(isTablet = false))
        assertEquals(TABLET_HINT_EXTRA_SCALE, hintExtraScaleFor(isTablet = true))
        assertEquals(1.35f, TABLET_HINT_EXTRA_SCALE)
    }
}
