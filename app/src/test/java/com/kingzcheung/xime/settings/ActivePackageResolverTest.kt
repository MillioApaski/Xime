package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 升级兼容：旧 prefs 没有 active_package_id 时的推断规则。
 *
 * 目标：只靠 current / enabled / 已安装 manifest / 文件存在性，推断出活动方案包；
 * 推不出来时兜底 builtin，绝不返回空。
 */
class ActivePackageResolverTest {

    private fun pkg(
        id: String,
        schemas: List<String>,
        available: List<String> = schemas,
        installedAt: Long = 0L,
    ) = ActivePackageResolver.ActivePackageCandidate(
        packageId = id,
        schemaIds = schemas.toSet(),
        availableSchemaIds = available.toSet(),
        installedAt = installedAt,
    )

    private val builtin = pkg("builtin", listOf("wubi86", "wubi86_pinyin", "pinyin_simp", "t9_pinyin"))

    @Test
    fun `current 属于第三方包时选第三方包`() {
        val rimeFrost = pkg("rime-frost", listOf("rime_frost", "rime_frost_t9"))
        assertEquals(
            "rime-frost",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "rime_frost_t9",
                enabledSchemas = listOf("rime_frost", "rime_frost_t9"),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `current 属于 builtin 时选 builtin`() {
        val rimeFrost = pkg("rime-frost", listOf("rime_frost", "rime_frost_t9"))
        assertEquals(
            "builtin",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "t9_pinyin",
                enabledSchemas = listOf("wubi86", "t9_pinyin"),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `current 是 handwriting 且只有一个第三方包时选它`() {
        val rimeFrost = pkg("rime-frost", listOf("rime_frost", "rime_frost_t9"))
        assertEquals(
            "rime-frost",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "handwriting",
                enabledSchemas = emptyList(),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `handwriting 且有多个包时按启用列表重叠选择`() {
        val a = pkg("pkg-a", listOf("a1", "a2"), installedAt = 1L)
        val b = pkg("pkg-b", listOf("b1", "b2"), installedAt = 2L)
        assertEquals(
            "pkg-a",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "handwriting",
                enabledSchemas = listOf("a1", "a2"),
                packages = listOf(builtin, a, b),
            ),
        )
    }

    @Test
    fun `已有 active 且包仍可用时保留`() {
        val rimeFrost = pkg("rime-frost", listOf("rime_frost", "rime_frost_t9"))
        assertEquals(
            "rime-frost",
            ActivePackageResolver.inferActivePackage(
                existingActive = "rime-frost",
                currentSchema = "wubi86",
                enabledSchemas = listOf("rime_frost_t9"),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `已有 active 对应包已卸载时重新推断`() {
        val rimeFrost = pkg("rime-frost", listOf("rime_frost", "rime_frost_t9"))
        assertEquals(
            "rime-frost",
            ActivePackageResolver.inferActivePackage(
                existingActive = "removed-pkg",
                currentSchema = "rime_frost_t9",
                enabledSchemas = listOf("rime_frost_t9"),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `current 源文件已删时按唯一的第三方包兜底`() {
        val rimeFrost = pkg(
            "rime-frost",
            schemas = listOf("rime_frost", "rime_frost_t9"),
            available = listOf("rime_frost"), // t9 源文件已删
        )
        assertEquals(
            "rime-frost",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "rime_frost_t9",
                enabledSchemas = listOf("rime_frost_t9"),
                packages = listOf(builtin, rimeFrost),
            ),
        )
    }

    @Test
    fun `没有任何包时兜底 builtin`() {
        assertEquals(
            "builtin",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "",
                enabledSchemas = emptyList(),
                packages = emptyList(),
            ),
        )
    }

    @Test
    fun `第三方包没有任何可用 schema 时兜底 builtin`() {
        val broken = pkg("broken", listOf("x1"), available = emptyList())
        assertEquals(
            "builtin",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "x1",
                enabledSchemas = listOf("x1"),
                packages = listOf(builtin, broken),
            ),
        )
    }

    @Test
    fun `多个包都没有启用重叠时选最近安装的`() {
        val older = pkg("older", listOf("o1"), installedAt = 10L)
        val newer = pkg("newer", listOf("n1"), installedAt = 20L)
        assertEquals(
            "newer",
            ActivePackageResolver.inferActivePackage(
                existingActive = null,
                currentSchema = "handwriting",
                enabledSchemas = emptyList(),
                packages = listOf(builtin, older, newer),
            ),
        )
    }
}
