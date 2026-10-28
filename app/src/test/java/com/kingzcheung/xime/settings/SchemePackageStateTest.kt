package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 状态层 reconcile 的纯逻辑：首次收敛/后续保留；不对特定方案做特判。 */
class SchemePackageStateTest {

    private val whiteFrostIntended = listOf(
        "rime_frost",
        "rime_frost_double_pinyin",
        "rime_frost_double_pinyin_mspy",
        "rime_frost_double_pinyin_sogou",
        "rime_frost_double_pinyin_flypy",
        "rime_frost_t9",
        "rime_frost_wubi86",
        "rime_frost_moqi_single_xh",
    )
    private val whiteFrostAvailable = (
        whiteFrostIntended + listOf(
            "bopomofo", "bopomofo_express", "bopomofo_tw",
            "cangjie5", "cangjie5_express", "radical_pinyin",
            "stroke", "melt_eng", "melt_eng_t9",
        )
        ).toSet()

    @Test
    fun `升级首启按包默认集合收敛并保留当前方案`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = true,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "rime_frost_double_pinyin_sogou",
            legacyEnabled = whiteFrostAvailable.toList(),
        )
        // 只启用包内 default.yaml 的 8 个；sogou 已在其中；stroke 不特殊启用
        assertEquals(whiteFrostIntended, enabled)
        assertEquals(false, "stroke" in enabled)
    }

    @Test
    fun `首启时当前方案是可选方案也保留`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = true,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "bopomofo",
            legacyEnabled = whiteFrostAvailable.toList(),
        )
        assertEquals(true, "bopomofo" in enabled)
        assertEquals(whiteFrostIntended.size + 1, enabled.size) // 默认 8 + 当前可选方案
        assertEquals(false, "stroke" in enabled)
    }

    @Test
    fun `后续启动保留用户启用子集`() {
        val userEnabled = listOf("rime_frost_t9", "stroke", "melt_eng")
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = false,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "rime_frost_t9",
            legacyEnabled = userEnabled,
        )
        assertEquals(userEnabled, enabled)
    }

    @Test
    fun `后续启动剔除不属于活动包的项`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = false,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "rime_frost_t9",
            legacyEnabled = listOf("rime_frost_t9", "wubi86", "t9_pinyin"),
        )
        assertEquals(listOf("rime_frost_t9"), enabled)
    }

    @Test
    fun `启用列表为空时回落到包默认集合`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = false,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "rime_frost_t9",
            legacyEnabled = emptyList(),
        )
        assertEquals(whiteFrostIntended, enabled)
    }

    @Test
    fun `当前方案不属于活动包时不会被加进启用列表`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = true,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "wubi86", // builtin 的 schema，不是活动包
            legacyEnabled = whiteFrostAvailable.toList(),
        )
        assertEquals(whiteFrostIntended, enabled)
    }

    @Test
    fun `当前是 handwriting 时不特殊处理`() {
        val enabled = SchemePackageState.computeEnabled(
            firstReconcile = true,
            intended = whiteFrostIntended,
            available = whiteFrostAvailable,
            current = "handwriting",
            legacyEnabled = whiteFrostAvailable.toList(),
        )
        assertEquals(whiteFrostIntended, enabled)
        assertEquals(false, "handwriting" in enabled)
    }
}
