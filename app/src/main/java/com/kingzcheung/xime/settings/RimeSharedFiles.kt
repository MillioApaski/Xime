package com.kingzcheung.xime.settings

import android.content.Context

/**
 * app 内置的共享基础文件。
 *
 * 范围是 assets/rime 顶层、除方案/词典/补丁之外的**文件**（目前 `default.yaml`、`symbols.yaml`，
 * 后续加 `key_bindings.yaml`、`punctuation.yaml`、`essay.txt` 等只需放进 assets 即自动生效）。
 *
 * 第三方方案普遍通过 `import_preset`、`__include: symbols:/` 引用它们，因此按「app 与方案共用」
 * 处理：部署前缺失即从 assets 补齐，方案安装不认领、卸载不删除。
 */
object RimeSharedFiles {
    private const val ASSETS_RIME_DIR = "rime"

    /** 内置共享文件名集合（每次按 assets 现算，加文件不需要改代码）。 */
    fun names(context: Context): Set<String> {
        val entries = context.assets.list(ASSETS_RIME_DIR) ?: return emptySet()
        return entries.filter { entry ->
            val isDir = (context.assets.list("$ASSETS_RIME_DIR/$entry") ?: emptyArray()).isNotEmpty()
            isSharedEntry(entry, isDir)
        }.toSet()
    }

    /** 顶层条目是否属于共享基础文件：排除目录、隐藏项、方案/词典/补丁文件。 */
    internal fun isSharedEntry(name: String, isDirectory: Boolean): Boolean =
        !isDirectory && !name.startsWith(".") &&
            !name.endsWith(".schema.yaml") && !name.endsWith(".dict.yaml") &&
            !name.endsWith(".custom.yaml")
}
