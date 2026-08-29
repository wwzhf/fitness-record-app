# 健身记录 Workout App

一个面向个人使用的纯本地健身记录 Android 应用。无账号、无联网、无广告——所有数据只存在你的手机里。

## 功能

**核心功能**

- **每日体重** — 训练页一键录入，一天一条，当天重复录入自动覆盖，附带记录时间
- **日历视图** — 内置自绘月历（周一起始），格子里直接显示当天体重 kg 和健身标记，点日期查看当日详情
- **健身计时** — 开始/结束自动计算时长；开始时间即时落库，App 被系统杀死后重新打开计时依然正确
- **自定义动作库** — 动作名完全自己起；有历史记录的动作只归档不删除，历史数据永远可查
- **训练记录** — 每次健身自定义标题（留空自动用日期），逐动作、逐组记录重量 × 次数；同一动作在同一次训练中只有一张卡片
- **历史查看** — 日历上点开任何一天的训练，可展开每个动作的每组数据、起止时间与总时长

**增补功能**

- **上次成绩参考** — 录组时自动显示该动作上次的全部组数据（如「上次：60kg×8, 60kg×10」），输入框预填上次第一组，方便渐进超负荷
- **体重趋势图** — Canvas 自绘折线图，近 30 天 / 近 90 天 / 全部切换，附最高 / 最低 / 平均摘要
- **数据备份** — 一键导出 JSON 全量备份；导入按合并策略恢复（同日体重覆盖、训练记录追加、同名动作复用），适合换机或重装

## 隐私

- `AndroidManifest.xml` **不申请任何权限，包括网络权限**——应用在物理上无法上传数据
- 系统自动备份（`allowBackup`）已关闭，数据不会经 Google 云同步
- 备份文件完全由你自己掌控（SAF 文件选择器自选位置）

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3（跟随系统深浅色 / 动态取色） |
| 数据库 | Room 2.6.1（KSP） |
| 架构 | 单模块 MVVM：Screen → ViewModel → Repository → DAO，手写 `AppContainer` 依赖注入 |
| 导航 | Navigation Compose |
| 序列化 | kotlinx-serialization（备份 JSON） |
| 测试 | JUnit4 + Robolectric（纯 JVM，无需真机） |
| 兼容 | minSdk 26（Android 8.0+），targetSdk 35 |

## 构建

**Android Studio**：直接打开项目根目录，Sync 后运行。

**命令行**（需 JDK 17 与 Android SDK 35，`local.properties` 指向 SDK 位置）：

```bat
gradlew.bat assembleDebug        :: 构建 Debug APK
gradlew.bat testDebugUnitTest    :: 运行 22 个单元测试
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 备份文件格式

趋势页导出的 JSON 结构（不含自增 id，组记录用数组下标引用会话与动作，跨设备导入无冲突）：

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

导入在单个数据库事务内完成，任一步失败整体回滚；`schemaVersion` 大于当前版本的备份会被拒绝。

## 项目结构

```
app/src/main/java/com/wc/workout/
├── WorkoutApp.kt          # Application + AppContainer（DI 容器）
├── MainActivity.kt
├── data/
│   ├── local/             # Room 实体（体重/动作/会话/组）与 DAO
│   ├── repository/        # 业务仓库 + 备份导入导出
│   └── backup/            # 备份 DTO
└── ui/
    ├── home/              # 训练页（今日体重 + 开始健身 + 进行中计时）
    ├── calendar/          # 日历（月视图 + 当日详情）
    ├── workout/           # 训练进行页（动作卡片 + 组录入）
    ├── library/           # 动作库
    ├── trend/             # 趋势图 + 备份入口
    └── common/            # 共享组件与格式化工具
```

## 版本

- **v1.0.0** — 首个完整版本：六大核心功能 + 上次成绩参考 + 体重趋势图 + JSON 备份

## 说明

个人自用项目，随手记录、数据自己持有。如果你也想用一个不联网、不注册、不催订阅的健身记录工具，欢迎自行构建使用。
