package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.util.FileLogger

/**
 * 单活动方案包的状态收敛（reconcile）。
 *
 * 只做“状态 → 派生文件”的一步：
 * - available = 活动包 manifest 内、源文件真实存在的 schema；
 * - 首次收敛（升级/全新安装）只启用包内 default.yaml 的 schema_list
 *   + 当前方案（若仍在包内）；不对 stroke/handwriting 等特定方案做任何特判；
 * - 之后保留用户自己的启用子集，只过滤掉不属于活动包/源文件已删的项；
 * - 不修改 current_schema：失效/手写模式由输入法启动路径自行兜底；
 * - 写 prefs、default.yaml（剥离 default.custom 的 schema_list）。
 *
 * 不部署、不删文件；部署由调用方走 ensureDeployment/产物校验。
 */
internal object SchemePackageState {
    private const val TAG = "SchemePackageState"
    private const val BUILTIN_PACKAGE_ID = SchemaManifestManager.BUILTIN_PACKAGE_ID

    /**
     * @return true=已按活动包收敛；false=没有活动包/活动包不可用，调用方应保持旧逻辑。
     */
    fun reconcile(context: Context): Boolean {
        val activeId = SettingsPreferences.getActivePackageId(context) ?: return false
        val packages = ActivePackageResolver.readInstalledPackages(context)
        val active = packages.firstOrNull { it.packageId == activeId } ?: return false
        val available = active.availableSchemaIds
        if (available.isEmpty()) return false

        val intended = intendedSchemas(context, activeId, available)
        val firstReconcile = !SettingsPreferences.isSchemeReconciled(context)
        val legacyEnabled = SettingsPreferences.getEnabledSchemaList(context).orEmpty()
        val current = SettingsPreferences.getCurrentSchema(context)

        val enabled = computeEnabled(
            firstReconcile = firstReconcile,
            intended = intended,
            available = available,
            current = current,
            legacyEnabled = legacyEnabled,
        )

        SettingsPreferences.setEnabledSchemaList(context, enabled)
        // 镜像 default.yaml 并剥离 default.custom.yaml 的 schema_list 覆盖块。
        SchemaManager.applyEnabledSchemasToDefaultYaml(context, enabled)
        SettingsPreferences.setSchemeReconciled(context, true)

        FileLogger.i(
            TAG,
            "reconcile: active=$activeId, first=$firstReconcile, " +
                "enabled=${enabled.joinToString()}, current=$current"
        )
        return true
    }

    /**
     * 纯函数（可单测）：首次收敛按“包内 default.yaml 开箱集合 + 当前方案”；
     * 之后保留用户启用子集，只剔除不属于活动包/源文件已删的项。
     *
     * 不对 stroke/handwriting 等特定方案做任何加减：它们要启用就必须出现在
     * 包内 default.yaml（内置包则是内置清单）里。
     */
    internal fun computeEnabled(
        firstReconcile: Boolean,
        intended: List<String>,
        available: Set<String>,
        current: String,
        legacyEnabled: List<String>,
    ): List<String> {
        val selected = if (firstReconcile) {
            intended + listOfNotNull(current.takeIf { it in available })
        } else {
            legacyEnabled.filter { it in available }
        }
        return selected.distinct().ifEmpty {
            intended.filter { it in available }.ifEmpty { available.take(1) }
        }
    }

    /** 活动包的“开箱启用集”：内置包固定 4 个；第三方包读包内 default.yaml。 */
    internal fun intendedSchemas(
        context: Context,
        packageId: String,
        available: Set<String>,
    ): List<String> {
        if (packageId == BUILTIN_PACKAGE_ID) {
            return SchemaManager.BUILTIN_SCHEMAS.filter { it in available }
        }
        val fromPackage = SchemaManager.readPackagedSchemaList(context, packageId)
            ?.filter { it in available }
            .orEmpty()
        // 没有 default.yaml 的包：先只启用第一个，避免把辅助 schema 全部拉起。
        return fromPackage.ifEmpty { available.take(1) }
    }
}
