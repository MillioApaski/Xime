package com.kingzcheung.xime.ui.keyboard

/**
 * 键盘内容与「宿主承载区」的尺寸协商（承载区 = IME 窗口 `android.R.id.inputArea` 实测高度）。
 *
 * 手机、平板上 IME 窗口是整屏：按屏高百分比算出的键盘期望高度恒小于承载区，
 * [fitContentHeightDp] 恒返回期望值本身 → 这些设备行为完全不变。
 *
 * 独立 IME 面板（如 Horizon OS 给输入法的悬浮面板）只给键盘一段区域，
 * 而 `configuration` 仍上报整块虚拟屏（Quest 3 实测 3302×1766dp、density 1.25，
 * 承载区却只有 1250×625px ≈ 1000×500dp）。按屏高百分比算出的 825dp 超出承载区后，
 * 容器贴底对齐会把顶边推到 y=-428px，只剩最后两行可见。
 * 这里以承载区为硬预算，保证键盘内容装得下。
 */
object KeyboardHostFit {

    /**
     * 把键盘内容期望高度夹取到宿主可用预算内。
     *
     * 两段式，保证普通设备零影响：
     * 1. 键盘本体装得下（`requested <= 承载区 - reserved`）→ 原样返回；
     *    撑高面板（快捷发送/工具面板）继续走既有的"容器向上溢出"行为。
     * 2. 键盘本体都装不下（独立 IME 面板）→ 撑高面板与键盘共用同一预算，
     *    否则面板会把键盘顶出承载区。
     *
     * @param requestedDp 期望内容高度（键盘本体，不含底部留白与撑高面板）
     * @param hostHeightDp 承载区高度（dp）；`<= 0` 表示尚未测得，不做约束
     * @param reservedDp 键盘本体之外固定占用的高度：底部留白 + 系统 inset 留白（+ 浮动拖动条）
     * @param extraPanelDp 撑高面板高度（快捷发送 / 工具面板）
     */
    fun fitContentHeightDp(
        requestedDp: Int,
        hostHeightDp: Int,
        reservedDp: Int,
        extraPanelDp: Int = 0,
    ): Int {
        if (hostHeightDp <= 0) return requestedDp
        val baseBudget = (hostHeightDp - reservedDp).coerceAtLeast(0)
        if (requestedDp <= baseBudget) return requestedDp
        val budget = (baseBudget - extraPanelDp).coerceAtLeast(0)
        return requestedDp.coerceAtMost(budget)
    }
}
