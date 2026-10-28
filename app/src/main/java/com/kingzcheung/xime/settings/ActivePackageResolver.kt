package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 单活动方案包模型下，为升级用户推断并持久化 [SettingsPreferences.getActivePackageId]。
 *
 * 旧的 prefs 没有这个键；状态层一旦按 active 包过滤，缺键会导致“所有方案都不可用”。
 * 这里只做一次**只读推断 + 写一个键**，不改 enabled_schemas/current_schema，不删文件。
 *
 * 推断顺序（置信度从高到低）：
 * 1. 已有 active 且包仍可用 → 保留；
 * 2. current_schema 在 enabled 里且落在某个包的可用 schema 里 → 该包；
 * 3. current 不在 enabled/空（含 handwriting 等 UI 模式）→ 唯一的非 builtin 包；
 * 4. 多个非 builtin 包 → 启用列表重叠最多者，其次最近安装者；
 * 5. 兜底 builtin（assets 始终可恢复），绝不把用户置为“无包可用”。
 */
internal object ActivePackageResolver {
    private const val TAG = "ActivePackageResolver"

    /** 方案包状态迁移版本；0/缺失=升级前。 */
    internal const val SCHEME_STATE_VERSION = 1

    private const val BUILTIN_PACKAGE_ID = SchemaManifestManager.BUILTIN_PACKAGE_ID

    internal data class ActivePackageCandidate(
        val packageId: String,
        /** manifest 声明的全部 schema（用于判断用户启用重叠）。 */
        val schemaIds: Set<String>,
        /** 磁盘上真实存在 schema 源文件的子集（用于判断 current 是否真的可用）。 */
        val availableSchemaIds: Set<String>,
        val installedAt: Long,
    )

    /** 幂等迁移：版本已到位时直接返回；失败不写版本，下次启动重试。 */
    fun migrateIfNeeded(context: Context, force: Boolean = false) {
        if (!force && SettingsPreferences.getSchemeStateVersion(context) >= SCHEME_STATE_VERSION) return
        try {
            val packages = readInstalledPackages(context)
            val active = inferActivePackage(
                // 备份恢复/手动修复时忽略备份里带回来的 active id（可能来自另一台设备），
                // 按恢复后的实际 current/manifests 重新推断。
                existingActive = if (force) null else SettingsPreferences.getActivePackageId(context),
                currentSchema = SettingsPreferences.getCurrentSchema(context),
                enabledSchemas = SettingsPreferences.getEnabledSchemaList(context).orEmpty(),
                packages = packages,
            )
            SettingsPreferences.setActivePackageId(context, active)
            SettingsPreferences.setSchemeStateVersion(context, SCHEME_STATE_VERSION)
            FileLogger.i(
                TAG,
                "Migrated active_package_id=$active; packages=" +
                    packages.joinToString { "${it.packageId}(${it.availableSchemaIds.size}/${it.schemaIds.size})" }
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to migrate active_package_id; will retry next start", e)
        }
    }

    /** 纯推断函数（可单测）：永不为空，最差返回 builtin。 */
    internal fun inferActivePackage(
        existingActive: String?,
        currentSchema: String,
        enabledSchemas: List<String>,
        packages: List<ActivePackageCandidate>,
    ): String {
        // builtin 即使 manifest/files 缺失也视为可兜底，避免升级后无包可用
        val valid = packages.filter {
            it.packageId == BUILTIN_PACKAGE_ID || it.availableSchemaIds.isNotEmpty()
        }

        existingActive?.let { active ->
            if (valid.any { it.packageId == active }) return active
        }

        // current 只有出现在启用列表里才作为强信号：user 的 UI 模式（如 handwriting）
        // 或历史幽灵 id 不在 enabled 中，交给后面的启用重叠/唯一包规则判断。
        if (currentSchema.isNotBlank() && currentSchema in enabledSchemas) {
            val owners = valid.filter { currentSchema in it.availableSchemaIds }
            if (owners.isNotEmpty()) {
                return owners.maxWithOrNull(
                    compareBy<ActivePackageCandidate>(
                        { overlap(it, enabledSchemas) },
                        { it.installedAt },
                    )
                )!!.packageId
            }
        }

        val nonBuiltin = valid.filter { it.packageId != BUILTIN_PACKAGE_ID }
        if (nonBuiltin.size == 1) return nonBuiltin.first().packageId
        if (nonBuiltin.size > 1) {
            // current 不可用（handwriting/幽灵）时，用启用列表重叠做次强信号；
            // 完全没有重叠再取最近安装的包。
            return nonBuiltin.maxWithOrNull(
                compareBy<ActivePackageCandidate>(
                    { overlap(it, enabledSchemas) },
                    { it.installedAt },
                )
            )!!.packageId
        }
        return BUILTIN_PACKAGE_ID
    }

    private fun overlap(candidate: ActivePackageCandidate, enabled: List<String>): Int =
        enabled.count { it in candidate.schemaIds }

    internal fun readInstalledPackages(context: Context): List<ActivePackageCandidate> {
        val rimeDir = SchemaManager.getRimeDir(context)
        val builtinSchemaIds = SchemaManager.BUILTIN_SCHEMAS.toMutableSet()
        var builtinInstalledAt = 0L
        val thirdParty = mutableListOf<ActivePackageCandidate>()

        val manifestDir = SchemaManifestManager.getManifestsDir(context)
        manifestDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.forEach { file ->
                try {
                    val json = JSONObject(file.readText())
                    val packageId = json.optString("schemeId")
                    if (packageId.isEmpty()) return@forEach
                    val files = json.optJSONObject("files") ?: JSONObject()
                    val schemaIds = files.keys().asSequence()
                        .filter { it.endsWith(".schema.yaml") }
                        .map { it.removeSuffix(".schema.yaml") }
                        .toSet()
                    val installedAt = parseInstalledAt(json.optString("installedAt"))
                    if (packageId == BUILTIN_PACKAGE_ID) {
                        builtinSchemaIds.addAll(schemaIds)
                        builtinInstalledAt = installedAt
                    } else {
                        thirdParty.add(candidate(packageId, schemaIds, rimeDir, installedAt))
                    }
                } catch (e: Exception) {
                    FileLogger.w(TAG, "Ignore invalid manifest ${file.name}: ${e.message}")
                }
            }

        return thirdParty + candidate(BUILTIN_PACKAGE_ID, builtinSchemaIds, rimeDir, builtinInstalledAt)
    }

    private fun candidate(
        packageId: String,
        schemaIds: Set<String>,
        rimeDir: File,
        installedAt: Long,
    ): ActivePackageCandidate = ActivePackageCandidate(
        packageId = packageId,
        schemaIds = schemaIds,
        availableSchemaIds = schemaIds.filter { File(rimeDir, "$it.schema.yaml").isFile }.toSet(),
        installedAt = installedAt,
    )

    private fun parseInstalledAt(value: String): Long = try {
        if (value.isBlank()) 0L
        else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(value)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
