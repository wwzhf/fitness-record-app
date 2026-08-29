# 个人健身记录 App 设计文档

- 日期：2026-08-28
- 状态：已经用户确认
- 项目：`D:\zcode\data\workout-app`（全新项目）

## 1. 背景与目标

面向作者个人使用的 Android 健身记录 app。所有数据仅存本地（Room），**不申请任何网络权限**，不做云同步。核心诉求：

1. 记录每日体重（附记录时间）
2. 内置日历查看历史体重（格子内显示 kg）
3. 记录每次健身的开始/结束时间及差值（时长）
4. 自定义动作库（动作名完全自命名）
5. 每次健身选一个自定义标题，逐动作、逐组记录重量与次数
6. 内置日历查看历史健身（格子显示健身标题）

## 2. 已确认的决策

| 决策点 | 结论 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Room（Android 原生） |
| 增补功能 | 数据导出/导入备份、记录时显示上次成绩、体重趋势折线图 |
| 增补未选 | 休息计时器（不做） |
| 体重规则 | 一天一条，当天重复录入覆盖旧值 |
| 动作库与记录关系 | 记录时引用动作库；用到库里没有的动作时顺手新建入库；有历史记录的动作只归档不删除 |
| 日历交互 | 格子内显示体重小字 + 健身标记，点击日期弹出当日详情 |
| v1.0.1 变更（2026-08-29） | 已结束的会话可编辑（时长与起止时间保持不变）；组录入手动展开/收起；日历格子显示当次健身标题；训练页标题可随时修改 |
| v1.0.2 变更（2026-08-29） | 日历当日详情可补记过去的健身：标题（留空用日期兜底）+ 开始/结束时间（HH:mm，默认当下/+1 小时，结束需晚于开始），创建后进入训练页补录动作与组；未来日期不可补记；归属按开始时间所在日 |
| v1.0.3 变更（2026-08-29） | 趋势图坐标轴重做：网格线 + 左侧 kg 刻度 + 底部 4 个日期刻度（按日期等间距），移除漂浮的最值标签；趋势页显示"该范围共 N 条记录"；日历支持左右滑动切换月份、点顶部"YYYY年M月 ▾"弹出年月选择器（年份 ± 切换 + 12 宫格选月） |
| v1.0.4 变更（2026-08-29） | 日历当日详情的体重行新增「删除」（二次确认），删除后日历格子与趋势图自动同步；Dao 层新增按日期删除 |

## 3. 非目标（Out of Scope）

- 云同步、账号系统、社交分享
- 休息计时器、组间提醒
- CSV 导出（JSON 备份已满足需求，CSV 以后想要再加）
- 磅（lb）等单位切换，固定 kg
- 复杂统计分析（除体重趋势线与基础摘要外）

## 4. 技术选型

| 项 | 选择 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.x（当前稳定版） | 官方首选 |
| UI | Jetpack Compose + Material 3 | 声明式 UI，现代默认 |
| 持久化 | Room | 本地关系型数据，Flow 支持 |
| 导航 | Navigation Compose | 标准方案 |
| 异步 | Coroutines + Flow | 标准方案 |
| 序列化 | kotlinx-serialization | 导出/导入 JSON |
| minSdk | 26 | 直接使用 `java.time`，无需 desugaring |
| compileSdk / AGP / 各库版本 | 实现时取当前稳定版 | 版本号不写入本 spec |
| 构建脚本 | Gradle Kotlin DSL + libs.versions.toml | 官方推荐 |

**权限**：不申请任何权限（含 INTERNET）。导出/导入文件通过 SAF（`CreateDocument` / `OpenDocument`）让用户自选位置，无需存储权限。

## 5. 架构

单模块 MVVM，Google 小型应用推荐结构：

```
Compose Screen ──> ViewModel ──> Repository ──> Room DAO ──> SQLite
       │                                  │
       └──────── StateFlow/State ◄────────┘
```

- 依赖注入：手写 `AppContainer`（`Application` 子类持有，提供 Database、各 Repository），不使用 Hilt。
- UI 层不直接访问数据库；一切经 ViewModel 暴露的 `StateFlow` / suspend 函数。
- 包结构（单模块内按层分包）：
  - `data/`（Room entity、dao、database、repository、backup）
  - `ui/`（按页面分子包：home、calendar、library、trend、workout）
  - `ui/theme/`（Material 3 主题）

## 6. 数据模型（4 张表）

```kotlin
@Entity(
    tableName = "weight_records",
    indices = [Index(value = ["dateEpochDay"], unique = true)]
)
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,      // LocalDate.toEpochDay()，按本地时区的"日"
    val weightKg: Double,
    val recordedAt: Long         // 实际录入时刻 epoch millis
)

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val isArchived: Boolean = false   // 有历史记录的动作只归档不物理删除
)

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,          // epoch millis，创建时立即落库
    val endTime: Long?            // null = 进行中
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"], childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"], childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val exerciseOrder: Int,       // 该动作在本次训练中的序号（一次训练内同一动作只出现一次）
    val setOrder: Int             // 该动作内第几组，从 1 开始
)
```

要点：

- `weight_records.dateEpochDay` 唯一索引实现"一天一条"；覆盖采用"先按日期查询、命中则更新 `weightKg` 与 `recordedAt`，未命中则插入"的事务式 upsert（不用 `INSERT OR REPLACE`，避免换 id）。
- `WorkoutSession.endTime = null` 即"进行中"，会话创建时 `startTime` 立即落库，App 被系统杀死后重启仍能正确恢复与结算。
- 动作删除规则：从未被 `workout_sets` 引用过的动作可直接物理删除；被引用过的动作只允许归档（`isArchived = true`，列表默认不显示，但历史记录仍能关联显示其名字）。DB 层 `RESTRICT` 兜底。

## 7. DAO 与核心查询

- **WeightDao**
  - `upsertByDate(record)`：事务内按 `dateEpochDay` 查后 update/insert
  - `getBetween(startEpochDay, endEpochDay): Flow<List<WeightRecord>>`（日历月视图用）
  - `getAll(): Flow<List<WeightRecord>>`（趋势图用，按日期升序）
- **ExerciseDao**
  - `observeAll(activeOnly: Boolean)`、归档查询
  - `insert / rename / setArchived`
  - `findByName(name)`（导入去重、新建判重用）
  - `countSetsForExercise(id)`（删除前判断是否被引用）
- **WorkoutDao**
  - `insertSession / setEndTime / deleteSession`
  - `observeOngoing(): Flow<WorkoutSession?>`（`endTime IS NULL`，最多一个：新建前若已有进行中会话则先引导处理）
  - `getSessionsBetweenTime(start, end)`（日历按天/月查询）
  - `getSessionWithSets(sessionId)`：`@Transaction` + JOIN 返回动作名，详情页展示 动作→组
  - 上次成绩（Repository 层两步实现，不做复杂 SQL）：
    1. 查该动作最近一次（排除当前会话）所在的 sessionId
    2. 按 `exerciseOrder, setOrder` 取该会话下此动作全部组记录
- **导出**：四表全量读取。

## 8. 页面与导航

底部导航 4 个 tab（Navigation Compose）：

| Tab | 页面 | 内容 |
|---|---|---|
| 训练 | HomeScreen | 今日体重卡片（未录入则显示快捷输入；已录入显示数值与时间，可修改覆盖）；进行中会话卡片（标题 + 每秒刷新的已用时 + 点按继续）；「开始健身」按钮 |
| 日历 | CalendarScreen | 自绘月历网格（7 列 `Canvas`/`Layout`，不引第三方日历库）；月份左右切换；每格：日期数字、体重 kg 小字（有则显示）、当次健身标题小字（最多 2 个，超出显示 +n）；点击日期弹出 BottomSheet 当日详情：当日体重 + 各次健身卡片（可展开 动作→各组 重量×次数、开始/结束时间与时长）；已结束的会话通过「编辑」进入训练页修改标题与组数据（时长与起止时间保持不变）；「删除该次记录」（二次确认，级联删组）保留 |
| 动作库 | ExerciseLibraryScreen | 动作列表（活跃动作；底部「已归档」折叠区）、顶部搜索、新建动作（重名拒绝并提示）、点击重命名、归档/删除（按第 6 节规则） |
| 趋势 | TrendScreen | 体重折线图（Canvas 自绘：x=日期、y=kg；范围切换 近30天/近90天/全部；摘要行显示最高/最低/平均；空数据显示占位文案）；数据导出按钮；数据导入按钮 |

全屏路由：`workout/{sessionId}` 训练进行页（见第 9 节）。

主题：Material 3，跟随系统深浅色；个人使用不做自定义主题配置页。

## 9. 关键流程

### 9.1 记录一次健身

1. 训练 tab 点「开始健身」→ 弹窗输入标题：文本框 + 历史标题下拉（最近去重 10 条）；留空提交时以当天日期兜底（如 `2026-08-28 训练`）。
2. 创建 `WorkoutSession(startTime = now, endTime = null)` 立即落库，跳转训练进行页。若已存在进行中会话，按钮文案变为「继续健身」直接进入该会话。
3. 训练进行页：
   - 顶部：标题（旁有编辑按钮，弹窗改名，不能为空，进行中与已结束均可改）+ 已用时计时器（`now - startTime` 每秒刷新；不依赖前台服务，App 在后台或被杀后回来都按 `startTime` 重算，天然正确）；已结束的会话显示当时的总时长，不显示计时器与结束/放弃按钮。
   - 「添加动作」→ BottomSheet 动作选择器：列表 + 搜索；搜不到时提供「新建"输入词"并加入动作库」；同一动作在本页只允许一个卡片（重复添加时滚动定位到已有卡片）。
   - 每个动作卡片：动作名 + 组记录列表 + 录入行（重量输入框、次数输入框、「添加」按钮）。
   - **预填与上次参考**：进入动作录入时，输入框预填该动作上一次**第一组**的重量与次数；录入行上方显示灰字「上次：60kg×8, 60kg×10, 55kg×12」列出上次全部组（无历史记录则不显示）。
   - 录入一组：录入行默认收起，点「+ 添加一组」展开，录完一组自动收起（重量保留、次数清空，方便手动再录）；校验 `weightKg > 0` 且 `reps > 0`（否则「添加」禁用）。
   - 点击已有组行 → 编辑对话框（修改重量/次数或删除该组）。
   - 「结束健身」→ 确认对话框（若本次 0 组，额外提示"未记录任何组"）→ `setEndTime(now)` → 返回训练 tab。
   - 「放弃本次」→ 二次确认 → `deleteSession`（级联删除组记录）。

### 9.2 体重录入

训练 tab 快捷卡片或日历详情内录入/修改；同一日期重复提交即覆盖（第 6 节 upsert）；`recordedAt` 记录实际提交时刻。

### 9.3 日历

月视图数据 = 该月 `WeightRecord` + 该月 `WorkoutSession` 两个 Flow 合并渲染。当日同时有两者时格内体重小字 + 健身标记并存；点击任意日期查看完整详情。

## 10. 备份：导出与导入

**格式**（`workout-backup-YYYYMMDD-HHmm.json`，schemaVersion 起始 1）：

```json
{
  "schemaVersion": 1,
  "exportedAt": 1756339200000,
  "weights":   [ { "dateEpochDay": 20659, "weightKg": 72.5, "recordedAt": 1756252800000 } ],
  "exercises": [ { "name": "卧推", "createdAt": 1756252800000, "isArchived": false } ],
  "sessions":  [ { "title": "推日", "startTime": 1756252800000, "endTime": 1756256400000 } ],
  "sets":      [ { "sessionIndex": 0, "exerciseIndex": 0, "weightKg": 60, "reps": 8, "exerciseOrder": 1, "setOrder": 1 } ]
}
```

导出文件内**不存自增 id**，用 `sessionIndex` / `exerciseIndex`（数组下标）引用，规避跨设备 id 冲突。

**导入算法**（单事务，任一步失败整体回滚并提示错误，不产生半份数据）：

1. 校验 `schemaVersion`，不匹配（更大版本）则拒绝。
2. 动作：按 `name` 查本地，存在则复用其 id（即使本地同名动作处于归档态也复用），否则插入；建立 index→本地 id 映射。若备份中该动作被本次导入的会话引用且本地同名动作已归档，自动取消归档。
3. 体重：逐条按 `dateEpochDay` upsert（本地同日数据被备份覆盖）。
4. 会话：逐条插入生成新 id；建立 index→新 id 映射。
5. 组：按两个映射生成 `sessionId` / `exerciseId` 后插入。
6. 完成后提示导入统计（如"导入 30 天体重、12 次训练"）。

导出经 SAF `CreateDocument("application/json")`，导入经 `OpenDocument`，均无需存储权限。

## 11. 错误处理与边界情况

| 场景 | 处理 |
|---|---|
| 重量/次数非法（≤0、非数字、为空） | 「添加」按钮禁用，输入框标红提示 |
| 标题为空 | 自动用日期兜底标题，不阻塞 |
| 动作重名 | 拒绝新建并提示"已存在同名动作" |
| 删除被引用的动作 | 不提供删除入口，只提供归档；DB `RESTRICT` 兜底 |
| 已有进行中会话再点开始 | 不新建，进入现有会话 |
| 结束时 0 组 | 确认对话框额外警示 |
| 放弃会话 | 二次确认后级联删除 |
| 数据库操作失败 | Snackbar 报错，数据保持一致（Room 事务保证） |
| 导出/导入 IO 失败或用户取消 | 静默取消 / Snackbar 报错 |
| 导入版本不匹配 | 拒绝并说明原因 |
| 各页空数据 | 空状态占位文案（如日历月无记录、动作库为空、无进行中会话） |
| 时区 | 日期一律按系统默认时区的本地日；跨月/跨天的会话归属以 `startTime` 所在日为准 |

## 12. 测试策略

个人项目，适度覆盖：

- **DAO 层**（Room in-memory + Robolectric，纯 JVM 运行无需真机）：
  - 体重同日 upsert 覆盖正确（id 不变、值与 `recordedAt` 更新）
  - 月范围查询边界（月初/月末当天）
  - 上次成绩两步查询（多会话、排除当前会话、无历史）
  - 会话删除级联清除组记录；`RESTRICT` 外键生效
  - 进行中会话查询（`endTime IS NULL`）
- **Repository / ViewModel**：in-memory Room 集成式单元测试（合并 Flow、导入映射算法、标题兜底逻辑）。
- **备份**：JSON 序列化往返测试；导入合并策略（动作去重、体重覆盖、id 重映射）测试。
- **UI**：手动验收为主，覆盖第 9 节全部流程。

## 13. 实现里程碑（对应 docs/superpowers/plans/2026-08-28-workout-app.md 的任务划分）

1. 项目脚手架：Gradle 配置、主题、`AppContainer`、底部导航空页
2. 数据层：Entity/DAO/Database + DAO 测试
3. 体重：录入 + 日历月视图 + 趋势图
4. 动作库页
5. 健身会话：创建/训练进行页/结束 + 组录入 + 上次成绩参考
6. 日历健身详情、导出/导入
7. 收尾：空状态打磨、整体手动回归
