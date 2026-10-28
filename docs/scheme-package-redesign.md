# 方案集（方案包）模块重设计

> 目标：把「装/卸/启用/选择/部署」从一堆互相兜底的补丁，收敛成**一个包、一份状态、一条安装路径、一个部署入口**。
> 本文是设计稿，不含代码改动；落地按第 7 节分阶段进行。

## 0. 结论先行

1. **一个方案集 = 一个自包含的包**。同一时刻，`files/rime` 里只放**一个**包的内容。
2. **装包 = 校验归档与空间 → 写意图 → 用户数据旁置 → 清空 rime → 解压 → 归位 → 部署**。第 4 步之前失败，`files/rime` 一个字节不动。
3. **状态只有一份**（Android 层）。`default.yaml` 的 `schema_list` 是部署前由状态生成的**派生物**，不再有 reconcile / merge / 五个一次性迁移标记。
4. **用户数据按白名单保留**（`xime.custom.yaml`、`themes/`、`fonts/`、`custom_phrase*.txt`），其余随包丢弃；不按"文件归属"追踪。删除 `.registry.json`、`.manifests/`、`.package-presets/`、共享预设回滚、active 包推断、启用列表收敛。
5. 预计净删约 2000 行：`SchemaManifestManager`(760) + `SharedPresetOverrides`(94) + `SchemePackageState`(98) + `ActivePackageResolver`(164) + `SchemaWorkspace`(116) + `SchemaManager` 里约 600 行装/卸/导入分支。

## 1. 现状：根因（不是"某个 bug"，是结构）

### 1.1 一棵目录同时服务四类文件

`files/rime` 既是 librime 的 `user_data_dir`，又是用户配置目录、app 派生文件目录、部署产物目录：

| 类别 | 例子 | 谁写 |
| --- | --- | --- |
| 包内容 | `*.schema.yaml`、`*.dict.yaml`、`opencc/`、`lua/` | 解压 | 
| 用户数据 | `*.userdb/`、`*.custom.yaml`、`xime.custom.yaml`、`default.custom.yaml`、`custom_phrase*.txt`、`themes/`、`fonts/`、`sync/` | 用户 / app |
| 派生文件 | `default.yaml` 的 `schema_list`、`*_merged.dict.yaml`、`custom_phrase.txt` | app |
| 引擎产物 | `build/*.bin`、`userdb`、`sync/` | librime |

任何"整目录"操作都会误伤，任何"逐文件"操作都要先知道归属 —— 于是长出了登记表、清单、共享白名单三套机制。

### 1.2 归属追踪无法闭合

`.manifests/{pkg}.json` 记录"某包安装时释放了哪些文件"，但：

- app 自己也会往同一棵树里生成文件（`*_merged.dict.yaml`、`custom_phrase.txt`），它们不属于任何包；
- 共享预设（`symbols.yaml`、`default.yaml`）同名不同内容，只能再加一层 `.package-presets/` 留档 + 卸载回滚；
- 单文件导入没有包，只好用 `manifestPackageIdFor(name)` 把路径展平**造一个假包 id**；
- 换包后旧包的 manifest 仍然留在 `.manifests/` 里 → `getInstalledPackages()` 声称装了多个包，而磁盘上只有一套内容 → 再写一个 `ActivePackageResolver` 用"启用列表重叠度 + 安装时间"猜哪个是真的。

**结论：用"记录文件归属"来支持"叠加安装"这条路走不通。** 现在的代码已经改成整目录替换（`SchemaWorkspace`），但只改了一半：

### 1.3 整目录替换只做了一半（当前工作区状态）

- `installFromMarketToRime` 允许替换，但 `default.yaml` 仍被 `isProtectedImportName` 挡在包外 → 包作者的 `default.yaml` 被丢弃，只能靠 `readPackagedSchemaList()` 从归档里**偷读** `schema_list`；
- 替换后仍然 `createManifest()`，`.manifests` 继续增长，`getInstalledPackages()` 继续撒谎；
- `SharedPresetOverrides` 继续按包留档共享预设；
- **最严重：替换会连带删掉用户数据** —— 无差别删除 `*.userdb/`、`xime.custom.yaml`（键盘/主题配置）、`themes/`、`fonts/`、`custom_phrase*.txt`。用户装一次新方案集，键盘习惯、背景图、自定义短语一起没了。

### 1.4 启用列表有五个来源

`prefs.enabled_schemas`（声称是真相）+ `default.yaml:schema_list`（派生）+ `default.custom.yaml`（历史）+ `builtin_schemas_merged`（一次性补齐标记）+ `mergeBuiltinSchemas()`（每次读取都可能回补）+ `SchemePackageState.reconcile()`（首次、后续两套语义）。任何一处顺序错，就会把列表写回去，或与部署 hash 互相触发。

### 1.5 `current_schema` 双写 + 引擎快照滞后

prefs 与 librime `user.yaml` 的 `var/previously_selected_schema` 双写；而 `get_schema_list` 读的是**上一次部署**编译进 `build/default.yaml` 的列表。于是"方案已编译、可切换"却要被 `isSchemaSwitchable(available ∪ compiled)` 这种补丁救回来。

### 1.6 部署触发靠"hash + 互相补文件 + 产物校验"

`ensureBuiltinBaseFiles()` 补共享文件会改 hash；`ensureDefaultYaml()` 再补一次；`PersonalDictManager` 每次启动都写补丁（hash 必变）；产物缺失又会反过来触发维护。再加上 librime 部署**失败不返回错误**，只能逐方案校验产物。最终表现：冷启动重编 30s、"部署成功但方案没编译"、"切方案提示未部署"。

### 1.7 UI 语义与磁盘模型矛盾

"方案"页把启用开关、当前方案、部署按钮混在一列；"已导入"页可以多选卸载包；安装时弹"是否卸载冲突方案"——但按现在的模型，**装包本来就要清空 rime**，那个冲突对话框是在描述一个已经不存在的问题。

## 2. 设计目标与不变量

- **I1** `files/rime` = 包内容 ∪ 用户数据 ∪ 引擎产物，三类**互斥**，且判定规则只写在一处。
- **I2** 装/卸/切换失败不产生不可恢复状态：第 4 步之前失败 `files/rime` 一字节不动；之后失败可从 `market/<id>/` 幂等重来，白名单用户数据在 `install-hold/` 中零拷贝旁置。
- **I3** 安装结束即可输入：部署在安装流程内完成，失败有结构化原因，不留"半部署"状态给下次启动。
- **I4** 白名单内的用户数据（3.2 的 4 项）跨包保留，任何装/卸/切换都不丢；白名单外的一律随包丢弃，不静默带走。
- **I5** 同一时刻只有一个包的内容；不存在"两套文件混在一起"的中间态，也不存在"清单说装了、磁盘上没有"。
- **I6** 状态只有一份，派生文件随时可以从状态重建；不依赖"上次是什么"标记。
- **I7** 启动时不需要"猜"：要么状态与内容一致，要么按状态重建内容。
- **I8** 同一个包重装（升级）不重置用户选择（启用了哪些方案、当前方案）。

## 3. 目标模型

### 3.1 目录

```
files/
├── rime/                     # 活动工作区（librime user_data_dir）
│   ├── <包内容>               #   只来自当前包的归档，整目录归属一个包
│   ├── build/                #   librime 产物（不跨包保留，包内自带的收下）
│   ├── xime.custom.yaml      #   键盘/主题用户配置（跨包保留）
│   ├── custom_phrase*.txt    #   自定义短语（跨包保留）
│   ├── fonts/  themes/       #   用户字体/背景图（跨包保留）
│   ├── default.custom.yaml   #   每次物化从 assets 模板重新播种（用户补丁不保留）
│   ├── installation.yaml     #   由 prefs 的 installation_id 重建
│   └── default.yaml          #   包内自带或 app 基线；schema_list 由 app 重写
├── market/<pkgId>/           # 已下载/已导入的原始压缩包（**现状路径不动**，不复制第二份）
│   ├── xxx.zip               #   安装、切回、重装都直接读它
│   └── overlay/              #   仅当用户给这个包单独导入过文件时才建（切走暂存，切回恢复）
└── cache/install-hold/       # 安装瞬间旁置的用户数据（rename，不占新空间），装完即删
```

- **压缩包只有一份**，就在 `market/<pkgId>/`（本地导入的包今天也在这里，`market/import_20261028_.../`），不再另建 `packages/` 存档目录。
- 安装 = 直接解压 `market/<pkgId>/xxx.zip` 到 `rime`；切回同一个包 = 再解压一次，不重新下载。
- 内置包没有存档：它的"压缩包"就是 APK 里的 `assets/rime`。

### 3.2 三类文件的判定（唯一出处：`SchemeFiles`）

| 类别 | 判定 | 装包时 | 卸载/切换时 |
| --- | --- | --- | --- |
| 包内容 | 归档里除下列两类之外的一切 | 解压 | 丢弃 |
| 用户数据 | 见下（白名单，4 项） | **拒绝**（包作者不得塞用户文件） | 保留 |
| 引擎产物 | `build/` | 接受（包内预编译产物有价值） | 丢弃 |

**用户数据白名单（已确认）**：

```
xime.custom.yaml        键盘/主题/布局等 app 配置
themes/                 用户背景图
fonts/                  用户字体（xime.custom.yaml 里按 fonts/xxx.ttf 引用）
custom_phrase*.txt      自定义短语
```

**明确不保留**（随包丢弃）：`*.userdb/`、`*.userdb.txt`、`sync/`、`user.yaml`、`*.custom.yaml`、`default.custom.yaml`。

四条说明：

- 自造词（userdb）属于某个包的词典，换包后没有意义，直接丢弃；长期保留请用「词条同步」或「完整备份」。
- `*.custom.yaml` 是 app 生成 + 用户手改的混合体（个人词典补丁 / `custom_phrase` 翻译器注入），丢弃后由 `PersonalDictManager.ensureSchemaPacks()` 按需重建，用户手改部分不保留。
- `installation.yaml` 由 prefs 里的 `installation_id` 重建（不参与保留，删了也不影响同步身份）；`default.custom.yaml` 每次物化从 assets 模板重新播种，`schema_list` 块始终由 app 托管。
- `build/` 只做**单向**处理：包里的 build 收下，旧目录的 build 不带过去。

### 3.3 状态

```kotlin
// 唯一写入者：SchemeStore
data class SchemeState(
    val activePackageId: String?,       // 活动包；null 仅表示"没有任何可用包"
    val enabled: List<String>,          // 有序，⊆ 活动包内存在源文件的方案
    val current: String,                // 当前方案；必须 ∈ enabled（或 ""）
    val deployedFingerprint: String?,   // 上次成功部署的内容指纹
)

data class PackageInfo(                 // 不落盘成文件；需要时由 prefs + 压缩包扫描得到
    val id: String,                     // builtin / 市场 id / import_20261028_...
    val name: String,                   // 市场索引或压缩包文件名
    val version: String,                // 市场索引；本地导入为空
    val kind: Kind,                     // BUILTIN / MARKET / LOCAL
    val schemaIds: List<String>,        // 扫压缩包/已解压内容得到
    val overlayFiles: List<String>,     // 该包的单文件导入（prefs 里记名字）
    val state: InstallState,            // EXTRACTING / READY / DEPLOY_FAILED（prefs）
)
```

"已安装"不再是一个需要记录的状态：**使用中 = active，其余 = 有压缩包可切换**。因此不需要 `installedAt`，也不需要"装了但压缩包已删"这种半状态。

落盘：

- `SchemeState` 的字段沿用 prefs（`active_package_id` / `enabled_schemas` / `current_schema` / `deployment_hash`），备份链路已经覆盖；
- **不新增 meta.json**：包状态（`install_state`、`overlay_files`）就是状态的一部分，和 `active/enabled/current/指纹` 一起放 prefs；名称/版本来自市场索引，本地导入包用压缩包文件名。`market/<id>/` 里仍然只有压缩包（外加用户自己导入文件时才有 `overlay/`）。
- **写入顺序**：先写意图（prefs 一次事务）→ 再换内容 → 再部署。崩在中间时，启动自检会按意图用 `market/<id>/` 的压缩包重建内容，因此不存在"内容与状态长期不一致"。
- 删除：`scheme_state_version`、`scheme_reconciled`、`builtin_schemas_merged`、`default_custom_migrated`、`builtin_seeded` 五个一次性标记 —— 有状态自检后它们全部失去意义。

### 3.4 模块边界

```
scheme/
  SchemeFiles.kt      路径常量 + 三类判定（纯函数，可单测）
  SchemeStore.kt      状态读写（全在 prefs）、事务、迁移、ensure/repair
  SchemeArchive.kt    归档探测/校验/解压（zip / tar.gz / 单文件；剥壳目录；拒绝用户数据类）
  SchemeInstaller.kt  install / switch / reinstall / uninstall / restoreBuiltin / 单文件导入
  SchemeDeployer.kt   生成 default.yaml → 个人词典补丁 → 指纹 → deploy → 产物校验
  SchemeCatalog.kt    扫活动包内的方案（解析 schema yaml，供列表与选择）
```

保留 `SchemaManager` 的**纯解析**部分（`parseSchemaYaml` / switches / displayName / `replaceSchemaListBlock`）与 `RimeDependencyResolver`（包依赖），其余装/卸/导入/收敛分支全部删除。

### 3.5 部署（一个入口）

```
SchemeDeployer.deploy(context, force: Boolean = false): DeployResult
```

1. 生成 `default.yaml`：包自带则只重写 `schema_list` 节点，否则复制 app 基线再写；
2. `default.custom.yaml` 不存在时从 assets 模板播种；存在则只剥掉 app 代管的 `schema_list` 块（兼容老文件/用户手加），其余内容不动；
3. `PersonalDictManager.ensureSchemaPacks()`（幂等）；
4. 算内容指纹（enabled 的 `*.schema.yaml`、`*.dict.yaml`、`*.custom.yaml`、`default.yaml`、`default.custom.yaml`、`symbols.yaml`、`*_merged.dict.yaml`）；
5. 指纹一致且产物齐全 → 跳过；否则 `deploy()`（build 缺失/为空或强制 → 全量，否则增量）；
6. 逐方案校验产物，返回 `DeployResult(ok, missingSchemas, reason)`。

- 安装后：`force = true`（build 属于旧包，先删再全量）；
- 包内自带 `build/` 且解压保留 mtime → 指纹能对上、产物齐全 → 直接可用（省 20s+）；
- 稳态：只有"用户改了会影响编译的文件"才触发，不再是"每次启动都动一下 hash"。

### 3.6 启动自检（替代所有 reconcile / 迁移标记）

`SchemeStore.ensure(context)`（Application 预初始化与 IME 初始化共用，幂等）：

1. 活动包缺失或内容不完整（enabled 里某个 `.schema.yaml` 不存在）→ 从 `market/<id>/` 的压缩包重建（内置包从 `assets/rime`），重建不了则回内置；
2. `enabled` 过滤到"源文件真实存在"，空则用包的声明列表/首个方案；
3. `current` 不在 `enabled` 内 → 取首个 enabled；
4. 生成 `default.yaml`；
5. 指纹不一致 → 后台部署（键盘可用前完成）。

## 4. 流程

### 4.1 安装（市场 / 本地文件 / URL / 依赖包都走这里）

不做整目录暂存，**先删后解压**：压缩包只有一份（在 `market/<id>/`），失败可以重来一次，没必要再多占一份解压空间。

```
SchemeInstaller.install(context, source): InstallResult

1  解析来源 → market/<id>/（下载则先落盘，校验 sha256）
2  探测归档：顶层 <id>.schema.yaml 数量、共同壳目录、含用户数据类文件、解压后总体积
    · 无任何 .schema.yaml → Failed(NoSchema)，此时什么都没动
    · 含用户数据类文件 → 记 warning，解压时丢弃
3  空间预检：可用空间 ≥ 解压后体积 × 1.1，不足 → Failed(NoSpace)，此时什么都没动
4  写意图：prefs 一次事务（active/enabled/current + install_state=EXTRACTING）
5  关引擎
6  用户数据旁置：白名单 4 项 rename 到 cache/install-hold/（同分区，零拷贝）
7  清空 files/rime（包内容、build、userdb、sync、*.custom.yaml 一并删）
8  解压 market/<id>/xxx.zip → files/rime（zip-slip 防护、剥壳目录、保留 mtime）
9  用户数据归位：install-hold/ 的 4 项 rename 回 files/rime；清掉 install-hold/
10 部署（force）→ 成功 install_state=READY；失败 install_state=DEPLOY_FAILED（可点部署重试）
11 返回 InstallResult（失败原因结构化，供 UI 直出）
```

失败语义：
- **第 4 步之前**失败（下载失败、归档损坏、无方案、空间不足）→ 什么都没动，`files/rime` 与状态原样。
- **第 5~9 步**失败或进程被杀 → rime 是半成品，但压缩包与 `install-hold/` 都在：启动自检看到 `state=EXTRACTING` 就从第 6 步重跑（幂等），重跑仍失败才回内置。
- **第 10 步**失败 → 内容与状态已是新包，只差部署，UI 报「已安装，部署失败：缺 xxx」；指纹与 `install_state=READY` 只在成功后写入，不存在「半部署」被当成已部署。

### 4.2 卸载 / 切换

- **卸载活动包** = 切回内置包：用 `assets/rime` 作为来源跑 4.1 的第 5~10 步（内置包永远可重建），状态 active=builtin、enabled=内置清单、current=首个；`market/<id>/` 的压缩包保留（可再切回，也可手动删）。
- **删除某个包的存档** = 删 `market/<id>/`（压缩包 + overlay）并清掉 prefs 里该包的状态，不动 rime；若要删的正是使用中的包，先切回内置，并说明「删掉压缩包后这个包就切不回来了」。
- **切换** = 对目标包跑 4.1 的第 5~10 步；确认框说明「当前方案集的文件会被移除，键盘设置 / 短语 / 字体 / 背景图保留，自造词清空」。
- 不再有「冲突检测 → 先卸载别的包」流程：清空 rime 本来就是安装的一部分。

### 4.3 启用 / 禁用 / 选择

- 只改状态（prefs）+ 置 `deployedFingerprint=null` 标记"待部署"，**不写文件**；
- 禁用最后一个方案被拒绝（至少留一个）；
- 选择当前方案：若已部署（在 `get_schema_list` 里）→ `switchSchema`；否则提示"有未部署的更改，请先部署"（不再用 `compiled` 兜底猜）；
- 部署成功后由 `SchemeDeployer` 统一 `switchSchema(current)`。

### 4.4 单文件导入（.schema.yaml / .dict.yaml / .txt / 字体 / 图片）

- 字体 → `fonts/`、图片 → `themes/`：用户数据，直接落盘（不变）；
- `.schema.yaml` → 落到 rime 根 + 记入**活动包**的 `overlayFiles` + 自动启用；切走时随旧包进 `market/<旧包>/overlay/`，切回时恢复；
- `.dict.yaml` / `custom_phrase.txt` 等 → 同上（记入活动包 overlay），需要部署才生效；
- 归档（zip/tar.gz）→ 落到 `market/<import_id>/`（现状不变），等用户在方案包页点「安装」；
- 删除 `manifestPackageIdFor` 那套"假包"逻辑。

### 4.5 备份 / 恢复

- 备份：`files/rime`（现状）+ prefs（现有链路；压缩包不进备份，可重新下载）；
- 恢复：解包后跑一次 `SchemeStore.ensure()`；若 rime 内容与 active 包不符，按 3.6 重建；
- 旧备份里的 `schemas/manifests/` 与 `schemas/registry.json` 条目恢复时**忽略并删除**（迁移兼容）。

### 4.6 升级迁移（当前版本 → 新模型，一次性）

1. 读 `.manifests/*.json`：只为推断 active 与各包 schemaIds；压缩包本来就在 `market/<id>/`，不搬，也不写 meta；
2. `market/builtin/`（内置文件快照）直接删 —— 内置包来源是 assets，不再需要快照；
3. active = 现有 `active_package_id`，若其实存 schema 为空 → builtin；
4. enabled = 现有列表 ∩ 活动包实存；空 → 包声明列表；
5. current = 现有值，不在 enabled → 首个；
6. 删除 `.manifests/`、`.registry.json`、`.package-presets/`，清掉五个一次性 prefs 标记；
7. 生成 `default.yaml`；跑一次 `ensure()`。

迁移失败不置版本号，下次启动重试（沿用现有做法）。

## 5. UI 调整

| 页面 | 现状 | 新 |
| --- | --- | --- |
| 方案 | 所有方案混一列 + 开关 + 当前 + 部署按钮 | 顶部一张"当前方案集"卡（名称·来源·版本·方案数 → 进包管理）；下方只列**活动包内**方案（开关=启用，点按=切当前，右标"已编译/待部署"）；底部部署按钮，有待部署更改时高亮 |
| 本地方案 / 已导入（同一页面，两个入口两个标题） | 多选卸载 + 冲突对话框 | 合并成一页「方案包」：使用中的包（高亮，操作=移除→回内置）、可切换的已装包、已下载未装（安装）、内置方案集（恢复默认）。导入动作（文件选择器 / 无线导入 / URL）保留在此页顶栏，不再单开"已导入"入口 |
| 市场 | 下载 → 安装（弹冲突） | 下载 → 安装（确认框：会移除当前方案集内容，个人数据保留）→ 进度分"安装/部署"两段 |
| 冲突对话框 | `confirmInstallWithUninstall` | 删除 |

## 6. 删除清单

| 文件 | 行数 | 处理 |
| --- | --- | --- |
| `SchemaManifestManager.kt` | 760 | 删（prefs 状态替代清单；`isUserDataFile` 移入 `SchemeFiles`） |
| `SharedPresetOverrides.kt` | 94 | 删（包内容自带共享文件，无需回滚基线） |
| `SchemePackageState.kt` | 98 | 删（安装时一次算好，不再收敛） |
| `ActivePackageResolver.kt` | 164 | 删（active 由安装写入，不再推断） |
| `SchemaWorkspace.kt` | 116 | 删（不再有整目录暂存与 rename 交换；解压直接落 `rime`） |
| `SchemaManager.kt` 装/卸/导入部分 | ~600 | 删（迁到 `SchemeArchive` / `SchemeInstaller`） |
| `SchemaLocalViewModel.confirmInstallWithUninstall` | — | 删 |
| `BackupManager` 的 `schemas/manifests/`、`schemas/registry.json` 条目 | — | 旧条目恢复时忽略并删除（状态已在 prefs） |
| `SchemaSettingsViewModel` 的 reconcile 相关分支 | — | 删 |

## 7. 分阶段落地（每阶段独立可验证）

- **P1 状态与目录**：`SchemeFiles` + `SchemeStore`（含 4.6 迁移），删除 `ActivePackageResolver` / `SchemePackageState`，`getEnabledSchemas` / `getCurrentSchema` 改走 store。
  验收：老版本升级后，方案列表、当前方案、部署状态不变；启动不重复部署。
- **P2 安装/卸载单一路径**：`SchemeArchive` + `SchemeInstaller` + `SchemeDeployer`，白名单用户数据带入，装完即部署；删除 manifests / registry / shared-preset / 冲突流程；单文件导入归包（overlay）。
  验收：市场装 rime-ice、白霜、本地导入包，切换来回，`xime.custom.yaml` / 短语 / 字体 / 背景图不丢；解压中途 kill 进程 → 重启后自动重解压成功。
- **P3 引擎判定与启动自检**：`ensure()`、指纹收敛，删 `isSchemaSwitchable` / `mergeBuiltinSchemas` / 五个一次性标记。
  验收：冷启动不重编；切方案不再误报"未部署"。
- **P4 UI**：按第 5 节改页面与文案（保留现有 Material 3 风格）。
  验收：安装/切换/移除/部署四个动作各自文案与状态清晰，无冲突对话框。
- **P5 收尾**：备份格式与旧备份兼容（忽略 `schemas/manifests/`、`schemas/registry.json` 条目）、`docs/` 文档、DECISIONS.md 记录本次重设计。

每个阶段结束跑 `assembleDebug` + 单测 + 真机回归（安装/切换/移除/部署/白名单用户数据保留）。

## 8. 测试计划（纯 JVM 优先）

必须新增的可单测纯函数：

- `SchemeFiles.classify(path)`：白名单 4 项保留、`userdb`/`sync`/`*.custom.yaml`/`default.custom.yaml` 丢弃、包内 build 收下，含嵌套路径；
- `SchemeArchive.probe(entries)`：顶层方案数、壳目录、含用户数据类文件、解压后体积；
- `SchemeInstaller.spaceCheck(freeBytes, unpackedBytes)`：空间预检边界；
- `SchemeInstaller.initialEnabled(package)`：有 default.yaml / 无 default.yaml / 重装保留；
- `SchemeStore.migrate(fromManifests, prefs)`：四种存量形态；
- `SchemeStore.repair(state, sources)`：内容缺失、current 失效、enabled 空；
- `SchemeDeployer.fingerprint(files)`：与内容变化一一对应（改一个字节就变）。

真机场景（每阶段回归）：内置 → 市场包 → 另一市场包 → 内置；安装时断网/断进程；包内缺词典；包内塞 `xime.custom.yaml`（应被拒绝并记 warning）；单文件导入后切包再切回。

## 9. 已确认的取舍（2026-10-28）

1. **用户数据边界**：留在 rime 根目录，按白名单保留 `xime.custom.yaml`、`themes/`、`fonts/`、`custom_phrase*.txt`；其余（userdb / sync / `*.custom.yaml` / `default.custom.yaml`）随包丢弃。
   - 不搬去 `files/user/`：librime 必须在 rime 里读写 userdb/sync，搬家会引入"源文件 + 生成视图"两份，等于把刚消灭的漂移问题请回来。
   - **已知代价**：换包 = 自造词清零。保留通道是「词条同步」与「完整备份」，安装确认框里写明。
2. **部署策略**：信任包内预编译 `build/`；解压保留 mtime，产物齐全且指纹对得上就跳过编译，校验不通过再全量。`applyArchiveTimestamp` 已解决 mtime 顺序问题。
3. **重装同包（升级）**：保留用户当前启用的方案子集与当前方案（I8）。
4. **单文件导入归属**：归"当前包"，记入 `meta.overlayFiles`，切走时进 `market/<旧包>/overlay/`，切回恢复。
5. **包自带 `default.yaml`**：收下，app 只重写 `schema_list` 节点（不再从归档偷读）。
6. **不建 `packages/` 存档目录、也不加 `meta.json`**：压缩包本来就在 `market/<id>/`，包状态与选择状态一起放 prefs，「状态只有一份」。
7. **安装顺序**：先删后解压（不做整目录暂存），峰值磁盘只有新包；安全性靠「归档校验 + 空间预检 + 压缩包保留 + 启动自检重跑」。

## 10. 遗留风险

- **自造词丢失**：白名单不含 userdb，换包后旧自造词不可恢复（只能靠备份/同步）。若后续要保留，只需把 `*.userdb/` 加进白名单，不改变模型。
- **包内预编译 build 的信任**：不同 librime 版本产物格式可能不兼容；`SchemeDeployer` 的产物校验（magic / 关键文件齐全）必须保留，校验失败自动回退全量。
- **`xime.custom.yaml` 引用了旧包字体/背景图**：字体与主题都在白名单里，不受影响；但用户若在配置里手写了某个包内路径，切包后会失效，属于配置层问题，不在本模块兜底。
- **删掉压缩包 = 该包不可切回**：这是「只保留一份压缩包」的直接结果，删除入口必须说清楚；使用中的包不允许直接删档（先切回内置）。

## 11. 落地记录

- **P1 已完成（2026-10-28）**：`SchemeFiles` / `SchemeState(SchemeLogic)` / `SchemeStore` 接管状态；删除 `ActivePackageResolver`、`SchemePackageState` 与两个一次性 prefs 标记。
- **P2 已完成（2026-10-28）**：`SchemeArchive` / `SchemeInstaller` / `SchemeDeployer` / `SchemePackages` 落地；删除清单系统、共享预设回滚、目录暂存、冲突流程；单文件导入归当前包（`scheme-overlay/<id>/`）。
- **P3 已完成（2026-10-28）**：启动自检覆盖中断/内容不完整；删 `isSchemaSwitchable`、`mergeBuiltinSchemas`、`builtin_seeded`、`builtin_schemas_merged`。
- **P4 已完成（2026-10-28）**：方案页「当前方案集」卡 + 方案包页（恢复内置），删除 `schema_imported` 路由与两个遗留对话框。
- **待做**：P5（备份含 overlay/方案包信息、旧备份兼容、DECISIONS.md 记录）。
