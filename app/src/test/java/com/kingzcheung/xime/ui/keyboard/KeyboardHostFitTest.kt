package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 键盘高度与宿主承载区的协商（[KeyboardHostFit]）。
 *
 * 回归背景（issue #1152）：Horizon OS 给输入法的独立面板只有 1250×625px（density 1.25
 * ⇒ 1000×500dp），而 `configuration` 上报 3302×1766dp。按屏高百分比算出的横屏键盘
 * 高度 825dp 塞进 500dp 面板，容器贴底后顶边落到 y=-428px，只剩最后两行可见。
 */
class KeyboardHostFitTest {

    @Test
    fun `normal phone keyboard keeps its preferred height`() {
        // 竖屏 800dp 屏、键盘期望 280dp、底部留白 18dp：承载区是整屏，不夹取
        assertEquals(
            280,
            KeyboardHostFit.fitContentHeightDp(requestedDp = 280, hostHeightDp = 800, reservedDp = 18),
        )
    }

    @Test
    fun `landscape phone with tool panel keeps its preferred height`() {
        // 横屏 411dp 屏、键盘 228dp、底部留白 18dp：本体装得下 → 不动键高，
        // 撑高面板（180dp）沿用既有"容器向上溢出"的行为（普通设备零影响）
        assertEquals(
            228,
            KeyboardHostFit.fitContentHeightDp(
                requestedDp = 228,
                hostHeightDp = 411,
                reservedDp = 18,
                extraPanelDp = 180,
            ),
        )
    }

    @Test
    fun `quest panel shares one budget between keyboard and tool panel`() {
        // 本体已超预算（825 > 500-18）→ 面板与键盘共用预算：500-18-180 = 302dp
        assertEquals(
            302,
            KeyboardHostFit.fitContentHeightDp(
                requestedDp = 825,
                hostHeightDp = 500,
                reservedDp = 18,
                extraPanelDp = 180,
            ),
        )
    }

    @Test
    fun `quest panel shrinks keyboard to exactly fit the host area`() {
        // 625px / 1.25 = 500dp 面板；期望 825dp；预留 18dp 底部留白
        val fitted = KeyboardHostFit.fitContentHeightDp(
            requestedDp = 825,
            hostHeightDp = 500,
            reservedDp = 18,
        )
        assertEquals(482, fitted)
        // 键盘内容 + 预留 = 承载区，容器贴底后顶边正好落回 y=0，不再被裁
        assertEquals(500, fitted + 18)
    }

    @Test
    fun `unknown host height does not constrain`() {
        // 首帧前 inputArea 尚未测量（0）：保持原期望值，不能把键盘压成 0
        assertEquals(
            825,
            KeyboardHostFit.fitContentHeightDp(requestedDp = 825, hostHeightDp = 0, reservedDp = 18),
        )
    }

    @Test
    fun `overlay panel larger than host never yields negative height`() {
        assertEquals(
            0,
            KeyboardHostFit.fitContentHeightDp(requestedDp = 200, hostHeightDp = 500, reservedDp = 700),
        )
    }
}
