# 个人健身记录 App 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个纯本地存储的个人健身记录 Android app：每日体重、内置日历、健身计时、自定义动作库、逐组训练记录、日历查看历史、体重趋势图、JSON 备份导入导出。

**Architecture:** 单模块 MVVM：Compose Screen → ViewModel → Repository → Room DAO → SQLite。手写 `AppContainer` 依赖注入。会话创建即落库 `startTime`，`endTime=null` 表示进行中，计时由 `now - startTime` 推算。

**Tech Stack:** Kotlin 2.1.0、Jetpack Compose（BOM 2024.12.01、Material 3）、Room 2.6.1（KSP）、Navigation Compose 2.8.5、kotlinx-serialization 1.7.3、Robolectric 4.14.1 + JUnit4。

**Spec:** `docs/superpowers/specs/2026-08-28-workout-app-design.md`（实现细节以 spec 为准，本计划按 spec 逐条落地）

## Global Constraints

- applicationId / namespace：`com.wc.workout`；minSdk 26；compileSdk / targetSdk 35；JDK 17；JVM target 17。
- 版本为已知稳定组合（AGP 8.7.3 + Gradle 8.14）。仅当依赖解析失败时才允许同大版本内升级，禁止跨大版本。
- **AndroidManifest 不申请任何权限**（含 INTERNET）。文件导出/导入一律走 SAF（`CreateDocument` / `OpenDocument`）。
- 日期一律 `java.time`；DB 存 `dateEpochDay`（`LocalDate.toEpochDay()`）与 epoch millis。
- 体重一天一条：`dateEpochDay` 唯一索引 + "查后改/插"的 upsert（禁止 `INSERT OR REPLACE`，会换 id）。
- 有历史记录的动作只能归档（`isArchived`），不能物理删除；DB 外键 `RESTRICT` 兜底。
- UI 文案直接硬编码中文（个人 app，不做 i18n）。
- 测试：Robolectric `@Config(sdk = [35])`（若 Robolectric 初始化失败改用 `[34]`）、JUnit4、`runBlocking`（不用 `runTest`，避免与 Room 真实线程池死锁）。
- 本机 shell 为 cmd.exe：gradle 命令用 `gradlew.bat`；含空格路径必须加引号；`cd` 用 `cd /d`。
- 每个任务必须构建（与测试）全绿后才 commit；commit message 用英文 conventional commits。
- 状态收集统一用 `collectAsState`（`lifecycle-runtime-compose` 依赖已引入备用，不强求）。

## 文件结构总览

```
app/src/main/java/com/wc/workout/
├── WorkoutApp.kt                      # Application + AppContainer（DI 容器）
├── MainActivity.kt                    # 唯一 Activity
├── data/local/
│   ├── WeightRecord.kt  Exercise.kt  WorkoutSession.kt  WorkoutSet.kt
│   ├── SetWithExercise.kt             # JOIN 查询结果（组 + 动作名）
│   ├── WeightDao.kt  ExerciseDao.kt  WorkoutDao.kt
│   └── AppDatabase.kt
├── data/repository/
│   ├── WeightRepository.kt            # 含共享的 upsertWeightByDate 顶层函数
│   ├── ExerciseRepository.kt
│   ├── WorkoutRepository.kt
│   └── BackupRepository.kt            # 导出/导入（kotlinx-serialization）
├── data/backup/BackupModels.kt        # 备份 DTO（@Serializable）
└── ui/
    ├── theme/Theme.kt
    ├── common/VM.kt                   # VMFactory + viewModelWith
    ├── common/Format.kt               # 时间/时长/kg 格式化
    ├── common/WeightEditDialog.kt     # 体重录入弹窗（复用）
    ├── common/NameDialog.kt           # 文本命名弹窗（复用，带回错误提示）
    ├── common/UriIo.kt                # SAF uri 读写文本
    ├── WorkoutRoot.kt                 # Scaffold + 底部导航 + NavHost
    ├── home/HomeScreen.kt  home/HomeViewModel.kt
    ├── calendar/CalendarScreen.kt  calendar/CalendarViewModel.kt
    ├── library/ExerciseLibraryScreen.kt  library/ExerciseLibraryViewModel.kt
    ├── trend/TrendScreen.kt  trend/TrendViewModel.kt  trend/WeightLineChart.kt
    └── workout/WorkoutSessionScreen.kt  workout/WorkoutSessionViewModel.kt
        workout/AddExerciseSheet.kt  workout/SetDialogs.kt
app/src/test/java/com/wc/workout/
├── data/local/DaoTest.kt
├── data/repository/RepositoryTest.kt
└── data/repository/BackupTest.kt
```

---

### Task 1: 环境准备与项目脚手架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/wc/workout/WorkoutApp.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`, `values/themes.xml`, `values-night/themes.xml`, `values/colors.xml`, `drawable/ic_launcher_foreground.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: 无（起始任务）
- Produces: 可编译可启动的空 app；`AppContainer`（暂只含 `database: AppDatabase`）；`WorkoutTheme`；后续所有任务在此骨架上扩展

- [ ] **Step 1: 检查/安装 JDK 17**

Run: `java -version`
- 若输出 17.x：跳过。
- 否则 Run: `winget install --id Microsoft.OpenJDK.17 -e --accept-package-agreements --accept-source-agreements`，然后在**新的** shell 里重跑 `java -version` 确认（旧 shell PATH 不刷新）。

- [ ] **Step 2: 检查/安装 Android SDK（cmdline-tools）**

Run: `dir /b "%LOCALAPPDATA%\Android\Sdk\cmdline-tools"`
- 若已存在且有 `latest` 子目录，且 `%LOCALAPPDATA%\Android\Sdk\platforms` 里有 `android-35`：跳到 Step 3。
- 否则：

```bat
mkdir "%LOCALAPPDATA%\Android\Sdk\cmdline-tools"
curl -L -o "%TEMP%\cmdline-tools.zip" https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
powershell -Command "Expand-Archive -Path $env:TEMP\cmdline-tools.zip -DestinationPath $env:LOCALAPPDATA\Android\Sdk\cmdline-tools"
ren "%LOCALAPPDATA%\Android\Sdk\cmdline-tools\cmdline-tools" latest
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%
(for /l %i in (1,1,30) do @echo y) | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

Expected: 各包 "100% Computing updates..." 后安装完成，无报错。

- [ ] **Step 3: 安装 Gradle（仅用于生成 wrapper）**

Run: `gradle -v`（新 shell）。若不可用：`winget install --id Gradle.Gradle -e --accept-package-agreements --accept-source-agreements`。若新 shell 仍找不到，用完整路径 `"%LOCALAPPDATA%\Microsoft\WinGet\Links\gradle.exe"`。

- [ ] **Step 4: 生成 Gradle wrapper（必须在写入 build 文件之前，空配置状态下执行）**

```bat
cd /d D:\zcode\data\workout-app
gradle wrapper --gradle-version 8.14
```

Expected: `BUILD SUCCESSFUL`，生成 `gradlew.bat`、`gradle\wrapper\gradle-wrapper.jar` 等。**先做本步再写 build 文件**，否则 Gradle 9.x 运行 wrapper 任务会先评估插件引发版本不兼容。

- [ ] **Step 5: 写入全部脚手架文件**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "workout-app"
include(":app")
```

根目录 `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.5"
room = "2.6.1"
kotlinxSerialization = "1.7.3"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`.gitignore`:
```
*.iml
.gradle/
/local.properties
.idea/
.DS_Store
build/
/captures
.externalNativeBuild
.cxx
```

`local.properties`（路径按 Step 2 实际安装位置调整，正斜杠写法）:
```
sdk.dir=C:/Users/wc/AppData/Local/Android/Sdk
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wc.workout"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wc.workout"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`app/src/main/AndroidManifest.xml`（注意：**无任何 uses-permission**）:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".WorkoutApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.WorkoutApp">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`app/src/main/java/com/wc/workout/WorkoutApp.kt`:
```kotlin
package com.wc.workout

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wc.workout.data.local.AppDatabase

class WorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "workout.db").build()
}
```

`app/src/main/java/com/wc/workout/MainActivity.kt`:
```kotlin
package com.wc.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import com.wc.workout.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = "健身记录", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}
```

`app/src/main/java/com/wc/workout/ui/theme/Theme.kt`:
```kotlin
package com.wc.workout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun WorkoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MaterialTheme.colorScheme
        else -> MaterialTheme.colorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```
说明：API < 31 时回落到 `MaterialTheme` 默认配色，不引入额外颜色定义。

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">健身记录</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.WorkoutApp" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values-night/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.WorkoutApp" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#4CAF50</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`（简化杠铃图形）:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M28,50h8v8h-8z M72,50h8v8h-8z M40,45h6v18h-6z M62,45h6v18h-6z M48,51h12v6h-12z" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`（minSdk 26，自适应图标覆盖全部设备，无需位图）:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 6: 构建验证**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`（首次会下载依赖，可能需要数分钟；若某依赖版本解析失败，按 Global Constraints 允许的方式同大版本内升级后重试）。

- [ ] **Step 7: 提交**

```bat
cd /d D:\zcode\data\workout-app
git add -A
git commit -m "chore: scaffold android project (gradle, room-less ui skeleton)"
```

---

### Task 2: Room 数据层（实体 + DAO + 数据库）与 DAO 测试

**Files:**
- Create: `app/src/main/java/com/wc/workout/data/local/WeightRecord.kt`（内含全部 4 个实体与 SetWithExercise，见代码）
- Create: `app/src/main/java/com/wc/workout/data/local/WeightDao.kt`、`ExerciseDao.kt`、`WorkoutDao.kt`、`AppDatabase.kt`
- Test: `app/src/test/java/com/wc/workout/data/local/DaoTest.kt`

**Interfaces:**
- Consumes: Task 1 的构建骨架
- Produces（后续任务依赖的确切签名）:
  - `WeightRecord(id: Long = 0, dateEpochDay: Long, weightKg: Double, recordedAt: Long)`
  - `Exercise(id: Long = 0, name: String, createdAt: Long, isArchived: Boolean = false)`
  - `WorkoutSession(id: Long = 0, title: String, startTime: Long, endTime: Long?)`
  - `WorkoutSet(id: Long = 0, sessionId: Long, exerciseId: Long, weightKg: Double, reps: Int, exerciseOrder: Int, setOrder: Int)`
  - `SetWithExercise(@Embedded set: WorkoutSet, exerciseName: String)`
  - DAO 方法见下方代码（后续 Repository 只通过这些方法访问 DB）

- [ ] **Step 1: 写实体文件**

`app/src/main/java/com/wc/workout/data/local/WeightRecord.kt`（4 个实体同包分文件亦可，本计划集中放一个文件以减少文件数）:
```kotlin
package com.wc.workout.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日体重：dateEpochDay 唯一，一天一条，重复录入走 update（见 Repository upsert） */
@Entity(
    tableName = "weight_records",
    indices = [Index(value = ["dateEpochDay"], unique = true)]
)
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Double,
    val recordedAt: Long
)

/** 自定义动作库；有历史记录的动作只归档不删除 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val isArchived: Boolean = false
)

/** 一次健身会话；endTime == null 表示进行中 */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null
)

/** 某会话中某动作的一组记录 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
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
    val exerciseOrder: Int,
    val setOrder: Int
)

/** 组记录带动作名（JOIN 查询结果），用于训练详情页 */
data class SetWithExercise(
    @Embedded val set: WorkoutSet,
    @ColumnInfo(name = "exerciseName") val exerciseName: String
)
```

- [ ] **Step 2: 写三个 DAO 与 AppDatabase**

`app/src/main/java/com/wc/workout/data/local/WeightDao.kt`:
```kotlin
package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_records WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay")
    fun observeBetween(start: Long, end: Long): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records")
    suspend fun getAll(): List<WeightRecord>

    @Query("SELECT * FROM weight_records WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getByDate(dateEpochDay: Long): WeightRecord?

    @Insert
    suspend fun insert(record: WeightRecord): Long

    @Update
    suspend fun update(record: WeightRecord)
}
```

`app/src/main/java/com/wc/workout/data/local/ExerciseDao.kt`:
```kotlin
package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isArchived = 1 ORDER BY name")
    fun observeArchived(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("UPDATE exercises SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE exercises SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COUNT(*) FROM workout_sets WHERE exerciseId = :id")
    suspend fun countSetsForExercise(id: Long): Int

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

`app/src/main/java/com/wc/workout/data/local/WorkoutDao.kt`:
```kotlin
package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("UPDATE workout_sessions SET endTime = :endTime WHERE id = :id")
    suspend fun setEndTime(id: Long, endTime: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeOngoing(): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime")
    fun observeSessionsBetween(start: Long, end: Long): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime")
    suspend fun getSessionsBetween(start: Long, end: Long): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions")
    suspend fun getAllSessions(): List<WorkoutSession>

    @Insert
    suspend fun insertSet(set: WorkoutSet): Long

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setOrder")
    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets")
    suspend fun getAllSets(): List<WorkoutSet>

    @Query("SELECT MAX(exerciseOrder) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun maxExerciseOrder(sessionId: Long): Int?

    @Query("SELECT MAX(setOrder) FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun maxSetOrder(sessionId: Long, exerciseId: Long): Int?

    @Query(
        """SELECT ws.sessionId FROM workout_sets ws
           INNER JOIN workout_sessions s ON s.id = ws.sessionId
           WHERE ws.exerciseId = :exerciseId AND ws.sessionId != :currentSessionId
           ORDER BY s.startTime DESC LIMIT 1"""
    )
    suspend fun findLastSessionIdWithExercise(exerciseId: Long, currentSessionId: Long): Long?

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setOrder")
    suspend fun getSetsOfExercise(sessionId: Long, exerciseId: Long): List<WorkoutSet>

    @Query(
        """SELECT ws.*, e.name AS exerciseName FROM workout_sets ws
           INNER JOIN exercises e ON e.id = ws.exerciseId
           WHERE ws.sessionId = :sessionId
           ORDER BY ws.exerciseOrder, ws.setOrder"""
    )
    suspend fun getSetsWithExerciseNames(sessionId: Long): List<SetWithExercise>
}
```

`app/src/main/java/com/wc/workout/data/local/AppDatabase.kt`:
```kotlin
package com.wc.workout.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WeightRecord::class, Exercise::class, WorkoutSession::class, WorkoutSet::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
}
```

- [ ] **Step 3: 写 DAO 测试（先写测试，本步结束时它们无法编译/无法通过，即"红"）**

`app/src/test/java/com/wc/workout/data/local/DaoTest.kt`:
```kotlin
package com.wc.workout.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun weightUpdateKeepsOneRowPerDay() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.0, recordedAt = 1_000))
        val existing = dao.getByDate(100)!!
        dao.update(existing.copy(weightKg = 71.5, recordedAt = 2_000))

        val rows = dao.getAll()
        assertEquals(1, rows.size)
        assertEquals(71.5, rows[0].weightKg, 0.001)
        assertEquals(2_000, rows[0].recordedAt)
        assertEquals(existing.id, rows[0].id)
    }

    @Test
    fun weightUniqueIndexRejectsSecondRowSameDay() {
        val dao = db.weightDao()
        runBlocking { dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.0, recordedAt = 1_000)) }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 72.0, recordedAt = 2_000)) }
        }
    }

    @Test
    fun observeBetweenFiltersByRange() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 99, weightKg = 70.0, recordedAt = 1))
        dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.5, recordedAt = 2))
        dao.insert(WeightRecord(dateEpochDay = 131, weightKg = 71.0, recordedAt = 3))
        val rows = dao.observeBetween(100, 130).first()
        assertEquals(1, rows.size)
        assertEquals(100L, rows[0].dateEpochDay)
    }

    @Test
    fun sessionDeleteCascadesSets() = runBlocking {
        val sessionId = db.workoutDao().insertSession(WorkoutSession(title = "推日", startTime = 1_000))
        val exerciseId = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 1))
        db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))
        db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 60.0, reps = 6, exerciseOrder = 1, setOrder = 2))

        db.workoutDao().deleteSession(sessionId)

        assertTrue(db.workoutDao().getSetsForSession(sessionId).isEmpty())
        assertTrue(db.workoutDao().getAllSets().isEmpty())
    }

    @Test
    fun exerciseDeleteRestrictedWhenReferenced() {
        val exerciseId = runBlocking { db.exerciseDao().insert(Exercise(name = "深蹲", createdAt = 1)) }
        val sessionId = runBlocking { db.workoutDao().insertSession(WorkoutSession(title = "腿日", startTime = 1_000)) }
        runBlocking {
            db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 100.0, reps = 5, exerciseOrder = 1, setOrder = 1))
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.exerciseDao().deleteById(exerciseId) }
        }
    }

    @Test
    fun observeOngoingReturnsOnlyNullEndSession() = runBlocking {
        val dao = db.workoutDao()
        dao.insertSession(WorkoutSession(title = "旧训练", startTime = 1_000, endTime = 2_000))
        val ongoingId = dao.insertSession(WorkoutSession(title = "进行中", startTime = 3_000))
        assertEquals(ongoingId, dao.observeOngoing().first()!!.id)
        dao.setEndTime(ongoingId, 4_000)
        assertNull(dao.observeOngoing().first())
    }

    @Test
    fun lastSessionQueryExcludesCurrentAndOrdersByStartTime() = runBlocking {
        val dao = db.workoutDao()
        val e = db.exerciseDao().insert(Exercise(name = "硬拉", createdAt = 1))
        val s1 = dao.insertSession(WorkoutSession(title = "一", startTime = 1_000))
        val s2 = dao.insertSession(WorkoutSession(title = "二", startTime = 2_000))
        val s3 = dao.insertSession(WorkoutSession(title = "三", startTime = 3_000))
        dao.insertSet(WorkoutSet(sessionId = s1, exerciseId = e, weightKg = 80.0, reps = 5, exerciseOrder = 1, setOrder = 1))
        dao.insertSet(WorkoutSet(sessionId = s2, exerciseId = e, weightKg = 90.0, reps = 5, exerciseOrder = 1, setOrder = 1))

        assertEquals(s2, dao.findLastSessionIdWithExercise(e, s3))
        assertEquals(s1, dao.findLastSessionIdWithExercise(e, s2))
    }

    @Test
    fun getSetsWithExerciseNamesJoinsName() = runBlocking {
        val dao = db.workoutDao()
        val e = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 1))
        val s = dao.insertSession(WorkoutSession(title = "推日", startTime = 1_000))
        dao.insertSet(WorkoutSet(sessionId = s, exerciseId = e, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))
        dao.insertSet(WorkoutSet(sessionId = s, exerciseId = e, weightKg = 60.0, reps = 10, exerciseOrder = 1, setOrder = 2))

        val rows = dao.getSetsWithExerciseNames(s)
        assertEquals(2, rows.size)
        assertEquals("卧推", rows[0].exerciseName)
        assertEquals(60.0, rows[0].set.weightKg, 0.001)
        assertEquals(2, rows[1].set.setOrder)
    }
}
```
注意：`observeBetweenFiltersByRange` 里第一行 `assertEquals(listOf(100L, 130L), emptyList())` 是错误示范占位——**删除这一行**，只保留真实断言（写它只是为了在此提醒：不要写无意义断言）。

- [ ] **Step 4: 运行测试确认全绿**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，8 个测试全部 PASS（Robolectric 首次会下载 android-all jar，耗时数分钟；若 `sdk=[35]` 初始化报错，改 `@Config(sdk = [34])` 重试）。若编译失败，按报错修正实体/DAO 代码后重跑，**不得**为了让测试通过而改动断言语义。

```bat
git add -A
git commit -m "feat: room data layer with entities, daos and dao tests"
```

---

### Task 3: Repository 层

**Files:**
- Create: `app/src/main/java/com/wc/workout/data/repository/WeightRepository.kt`、`ExerciseRepository.kt`、`WorkoutRepository.kt`
- Modify: `app/src/main/java/com/wc/workout/WorkoutApp.kt`（AppContainer 增加三个仓库）
- Test: `app/src/test/java/com/wc/workout/data/repository/RepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 的全部实体与 DAO 方法、`AppDatabase`
- Produces:
  - `class WeightRepository(db: AppDatabase)`：
    `observeBetween(start: LocalDate, end: LocalDate): Flow<List<WeightRecord>>`；
    `observeAll(): Flow<List<WeightRecord>>`；`getByDate(date: LocalDate): WeightRecord?`；
    `saveWeight(date: LocalDate, weightKg: Double, recordedAt: Long = System.currentTimeMillis())`
  - 顶层 `suspend fun upsertWeightByDate(dao: WeightDao, record: WeightRecord)`（BackupRepository 复用）
  - `sealed interface ExerciseNameResult { data class Success(val id: Long); data object Duplicate }`
  - `class ExerciseRepository(db: AppDatabase)`：`observeActive()` / `observeArchived()`；
    `addExercise(name: String): ExerciseNameResult`；`rename(id: Long, newName: String): ExerciseNameResult`；
    `setArchived(id: Long, archived: Boolean)`；`tryDelete(id: Long): Boolean`；`isReferenced(id: Long): Boolean`；`getById(id: Long): Exercise?`
  - `class WorkoutRepository(db: AppDatabase)`：`startSession(title: String, now: Long = System.currentTimeMillis()): Long`；
    `endSession(id: Long, now: Long = System.currentTimeMillis())`；`abandonSession(id: Long)`；
    `observeOngoing(): Flow<WorkoutSession?>`；`observeSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<WorkoutSession>>`；
    `getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkoutSession>`；`getSession(id: Long): WorkoutSession?`；
    `recentTitles(limit: Int = 10): List<String>`；`addSet(sessionId: Long, exerciseId: Long, weightKg: Double, reps: Int): Long`；
    `updateSet(set: WorkoutSet)`；`deleteSet(id: Long)`；
    `getSetsWithExerciseNames(sessionId: Long): List<SetWithExercise>`；
    `lastPerformance(exerciseId: Long, currentSessionId: Long): List<WorkoutSet>`；
    `removeExerciseFromSession(sessionId: Long, exerciseId: Long)`

- [ ] **Step 1: 写 WeightRepository（含共享 upsert 顶层函数）**

`app/src/main/java/com/wc/workout/data/repository/WeightRepository.kt`:
```kotlin
package com.wc.workout.data.repository

import androidx.room.withTransaction
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.WeightDao
import com.wc.workout.data.local.WeightRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 按 dateEpochDay 覆盖式保存；BackupRepository 在自己的事务内复用 */
suspend fun upsertWeightByDate(dao: WeightDao, record: WeightRecord) {
    val existing = dao.getByDate(record.dateEpochDay)
    if (existing == null) {
        dao.insert(record)
    } else {
        dao.update(existing.copy(weightKg = record.weightKg, recordedAt = record.recordedAt))
    }
}

class WeightRepository(private val db: AppDatabase) {
    private val dao = db.weightDao()

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<WeightRecord>> =
        dao.observeBetween(start.toEpochDay(), end.toEpochDay())

    fun observeAll(): Flow<List<WeightRecord>> = dao.observeAll()

    suspend fun getByDate(date: LocalDate): WeightRecord? = dao.getByDate(date.toEpochDay())

    suspend fun saveWeight(date: LocalDate, weightKg: Double, recordedAt: Long = System.currentTimeMillis()) {
        db.withTransaction {
            upsertWeightByDate(dao, WeightRecord(dateEpochDay = date.toEpochDay(), weightKg = weightKg, recordedAt = recordedAt))
        }
    }
}
```

- [ ] **Step 2: 写 ExerciseRepository 与 WorkoutRepository**

`app/src/main/java/com/wc/workout/data/repository/ExerciseRepository.kt`:
```kotlin
package com.wc.workout.data.repository

import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import kotlinx.coroutines.flow.Flow

sealed interface ExerciseNameResult {
    data class Success(val id: Long) : ExerciseNameResult
    data object Duplicate : ExerciseNameResult
}

class ExerciseRepository(private val db: AppDatabase) {
    private val dao = db.exerciseDao()

    fun observeActive(): Flow<List<Exercise>> = dao.observeActive()
    fun observeArchived(): Flow<List<Exercise>> = dao.observeArchived()

    suspend fun addExercise(name: String): ExerciseNameResult {
        val trimmed = name.trim()
        if (dao.findByName(trimmed) != null) return ExerciseNameResult.Duplicate
        return ExerciseNameResult.Success(dao.insert(Exercise(name = trimmed, createdAt = System.currentTimeMillis())))
    }

    suspend fun rename(id: Long, newName: String): ExerciseNameResult {
        val trimmed = newName.trim()
        val existing = dao.findByName(trimmed)
        if (existing != null && existing.id != id) return ExerciseNameResult.Duplicate
        dao.rename(id, trimmed)
        return ExerciseNameResult.Success(id)
    }

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun isReferenced(id: Long): Boolean = dao.countSetsForExercise(id) > 0

    /** 只有从未被引用过的动作允许物理删除 */
    suspend fun tryDelete(id: Long): Boolean {
        if (isReferenced(id)) return false
        dao.deleteById(id)
        return true
    }

    suspend fun getById(id: Long): Exercise? = dao.getById(id)
}
```

`app/src/main/java/com/wc/workout/data/repository/WorkoutRepository.kt`:
```kotlin
package com.wc.workout.data.repository

import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()

    suspend fun startSession(title: String, now: Long = System.currentTimeMillis()): Long =
        dao.insertSession(WorkoutSession(title = title, startTime = now))

    suspend fun endSession(id: Long, now: Long = System.currentTimeMillis()) = dao.setEndTime(id, now)

    suspend fun abandonSession(id: Long) = dao.deleteSession(id)

    fun observeOngoing(): Flow<WorkoutSession?> = dao.observeOngoing()

    fun observeSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<WorkoutSession>> =
        dao.observeSessionsBetween(startMillis, endMillis)

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkoutSession> =
        dao.getSessionsBetween(startMillis, endMillis)

    suspend fun getSession(id: Long): WorkoutSession? = dao.getSession(id)

    /** 最近会话标题去重，供"开始健身"弹窗快捷选择 */
    suspend fun recentTitles(limit: Int = 10): List<String> =
        dao.getRecentSessions(50).map { it.title }.distinct().take(limit)

    suspend fun addSet(sessionId: Long, exerciseId: Long, weightKg: Double, reps: Int): Long {
        val sameExercise = dao.getSetsOfExercise(sessionId, exerciseId)
        val exerciseOrder = sameExercise.firstOrNull()?.exerciseOrder
            ?: ((dao.maxExerciseOrder(sessionId) ?: 0) + 1)
        val setOrder = (dao.maxSetOrder(sessionId, exerciseId) ?: 0) + 1
        return dao.insertSet(
            WorkoutSet(
                sessionId = sessionId, exerciseId = exerciseId,
                weightKg = weightKg, reps = reps,
                exerciseOrder = exerciseOrder, setOrder = setOrder
            )
        )
    }

    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set)
    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun getSetsWithExerciseNames(sessionId: Long): List<SetWithExercise> =
        dao.getSetsWithExerciseNames(sessionId)

    suspend fun lastPerformance(exerciseId: Long, currentSessionId: Long): List<WorkoutSet> {
        val sessionId = dao.findLastSessionIdWithExercise(exerciseId, currentSessionId) ?: return emptyList()
        return dao.getSetsOfExercise(sessionId, exerciseId)
    }

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) {
        dao.getSetsOfExercise(sessionId, exerciseId).forEach { dao.deleteSet(it.id) }
    }
}
```

- [ ] **Step 3: AppContainer 挂上仓库**

修改 `WorkoutApp.kt` 中 `AppContainer`（`WorkoutApp` 类不变）:
```kotlin
class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "workout.db").build()

    val weightRepository = WeightRepository(database)
    val exerciseRepository = ExerciseRepository(database)
    val workoutRepository = WorkoutRepository(database)
    val backupRepository = BackupRepository(database)
}
```
`BackupRepository` 在 Task 4 才创建——本步先**注释掉 `backupRepository` 这一行**并在 Task 4 启用，或直接跳到 Task 4 再改 AppContainer（二选一，推荐后者：本步 AppContainer 只加前三个仓库）。

- [ ] **Step 4: 写 Repository 测试**

`app/src/test/java/com/wc/workout/data/repository/RepositoryTest.kt`:
```kotlin
package com.wc.workout.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var weightRepo: WeightRepository
    private lateinit var exerciseRepo: ExerciseRepository
    private lateinit var workoutRepo: WorkoutRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        weightRepo = WeightRepository(db)
        exerciseRepo = ExerciseRepository(db)
        workoutRepo = WorkoutRepository(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun saveWeightTwiceSameDayUpdatesOneRow() = runBlocking {
        val today = LocalDate.of(2026, 8, 28)
        weightRepo.saveWeight(today, 72.0, recordedAt = 1_000)
        weightRepo.saveWeight(today, 71.4, recordedAt = 2_000)
        val rows = db.weightDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(71.4, rows[0].weightKg, 0.001)
        assertEquals(2_000, rows[0].recordedAt)
    }

    @Test
    fun addDuplicateExerciseReturnsDuplicate() = runBlocking {
        assertTrue(exerciseRepo.addExercise("卧推") is ExerciseNameResult.Success)
        assertEquals(ExerciseNameResult.Duplicate, exerciseRepo.addExercise("卧推 "))
    }

    @Test
    fun renameToExistingOtherNameReturnsDuplicate() = runBlocking {
        val a = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        exerciseRepo.addExercise("飞鸟")
        assertEquals(ExerciseNameResult.Duplicate, exerciseRepo.rename(a.id, "飞鸟"))
        assertTrue(exerciseRepo.rename(a.id, "上斜卧推") is ExerciseNameResult.Success)
    }

    @Test
    fun tryDeleteRules() = runBlocking {
        val e = exerciseRepo.addExercise("深蹲") as ExerciseNameResult.Success
        assertTrue(exerciseRepo.tryDelete(e.id))          // 未被引用，可删
        val e2 = exerciseRepo.addExercise("硬拉") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("腿日")
        workoutRepo.addSet(s, e2.id, 100.0, 5)
        assertFalse(exerciseRepo.tryDelete(e2.id))        // 被引用，拒绝删除
        assertTrue(exerciseRepo.isReferenced(e2.id))
    }

    @Test
    fun addSetAssignsOrdersCorrectly() = runBlocking {
        val e1 = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val e2 = exerciseRepo.addExercise("飞鸟") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("推日")
        workoutRepo.addSet(s, e1.id, 60.0, 8)
        workoutRepo.addSet(s, e1.id, 60.0, 10)
        workoutRepo.addSet(s, e2.id, 15.0, 12)
        val sets = db.workoutDao().getSetsForSession(s)
        assertEquals(3, sets.size)
        assertEquals(1, sets[0].exerciseOrder); assertEquals(1, sets[0].setOrder)
        assertEquals(1, sets[1].exerciseOrder); assertEquals(2, sets[1].setOrder)
        assertEquals(2, sets[2].exerciseOrder); assertEquals(1, sets[2].setOrder)
    }

    @Test
    fun startEndSessionAndOngoing() = runBlocking {
        val s = workoutRepo.startSession("推日", now = 1_000)
        assertEquals(s, workoutRepo.observeOngoing().first()!!.id)
        workoutRepo.endSession(s, now = 2_500)
        assertNull(workoutRepo.observeOngoing().first())
        assertEquals(1_500L, workoutRepo.getSession(s)!!.endTime!! - workoutRepo.getSession(s)!!.startTime)
    }

    @Test
    fun abandonRemovesSessionAndSets() = runBlocking {
        val e = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("推日")
        workoutRepo.addSet(s, e.id, 60.0, 8)
        workoutRepo.abandonSession(s)
        assertTrue(db.workoutDao().getAllSets().isEmpty())
        assertNull(workoutRepo.getSession(s))
    }

    @Test
    fun recentTitlesDedupesAndLimits() = runBlocking {
        workoutRepo.startSession("推日", now = 1_000)
        workoutRepo.startSession("拉日", now = 2_000)
        workoutRepo.startSession("推日", now = 3_000)
        workoutRepo.startSession("腿日", now = 4_000)
        val titles = workoutRepo.recentTitles(limit = 3)
        assertEquals(listOf("腿日", "推日", "拉日"), titles)
    }

    @Test
    fun lastPerformanceExcludesCurrentSession() = runBlocking {
        val e = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val s1 = workoutRepo.startSession("一", now = 1_000)
        workoutRepo.addSet(s1, e.id, 60.0, 8)
        workoutRepo.addSet(s1, e.id, 60.0, 10)
        workoutRepo.endSession(s1, now = 2_000)
        val s2 = workoutRepo.startSession("二", now = 3_000)
        val perf = workoutRepo.lastPerformance(e.id, currentSessionId = s2)
        assertEquals(2, perf.size)
        assertEquals(8, perf[0].reps); assertEquals(10, perf[1].reps)
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，Task 2 + Task 3 共 17 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bat
git add -A
git commit -m "feat: repositories for weight, exercise and workout with tests"
```

---

### Task 4: 备份导出/导入逻辑

**Files:**
- Create: `app/src/main/java/com/wc/workout/data/backup/BackupModels.kt`
- Create: `app/src/main/java/com/wc/workout/data/repository/BackupRepository.kt`
- Modify: `app/src/main/java/com/wc/workout/WorkoutApp.kt`（启用 `backupRepository`）
- Test: `app/src/test/java/com/wc/workout/data/repository/BackupTest.kt`

**Interfaces:**
- Consumes: Task 2 DAO（含 `getAll` 系列）、Task 3 `upsertWeightByDate`
- Produces:
  - `@Serializable data class BackupData(schemaVersion: Int, exportedAt: Long, weights: List<WeightBackup>, exercises: List<ExerciseBackup>, sessions: List<SessionBackup>, sets: List<SetBackup>)`
  - DTO 子类型见代码；备份文件内**不含自增 id**，用 `sessionIndex` / `exerciseIndex`（数组下标）引用
  - `class BackupRepository(db: AppDatabase)`：`export(): String`；`import(jsonText: String): ImportSummary`
  - `data class ImportSummary(weights: Int, exercises: Int, sessions: Int, sets: Int)`

- [ ] **Step 1: 写备份 DTO**

`app/src/main/java/com/wc/workout/data/backup/BackupModels.kt`:
```kotlin
package com.wc.workout.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int,
    val exportedAt: Long,
    val weights: List<WeightBackup>,
    val exercises: List<ExerciseBackup>,
    val sessions: List<SessionBackup>,
    val sets: List<SetBackup>
)

@Serializable
data class WeightBackup(val dateEpochDay: Long, val weightKg: Double, val recordedAt: Long)

@Serializable
data class ExerciseBackup(val name: String, val createdAt: Long, val isArchived: Boolean)

@Serializable
data class SessionBackup(val title: String, val startTime: Long, val endTime: Long?)

@Serializable
data class SetBackup(
    val sessionIndex: Int,
    val exerciseIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val exerciseOrder: Int,
    val setOrder: Int
)
```

- [ ] **Step 2: 写 BackupRepository**

`app/src/main/java/com/wc/workout/data/repository/BackupRepository.kt`:
```kotlin
package com.wc.workout.data.repository

import androidx.room.withTransaction
import com.wc.workout.data.backup.BackupData
import com.wc.workout.data.backup.ExerciseBackup
import com.wc.workout.data.backup.SessionBackup
import com.wc.workout.data.backup.SetBackup
import com.wc.workout.data.backup.WeightBackup
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import kotlinx.serialization.json.Json

data class ImportSummary(val weights: Int, val exercises: Int, val sessions: Int, val sets: Int)

class BackupRepository(private val db: AppDatabase) {
    private val weightDao = db.weightDao()
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun export(): String {
        val exercises = exerciseDao.getAll()
        val sessions = workoutDao.getAllSessions()
        val data = BackupData(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            weights = weightDao.getAll().map {
                WeightBackup(it.dateEpochDay, it.weightKg, it.recordedAt)
            },
            exercises = exercises.map { ExerciseBackup(it.name, it.createdAt, it.isArchived) },
            sessions = sessions.map { SessionBackup(it.title, it.startTime, it.endTime) },
            sets = workoutDao.getAllSets().mapNotNull { s ->
                val si = sessions.indexOfFirst { it.id == s.sessionId }.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                val ei = exercises.indexOfFirst { it.id == s.exerciseId }.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                SetBackup(si, ei, s.weightKg, s.reps, s.exerciseOrder, s.setOrder)
            }
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** 单事务导入，任一步失败整体回滚。合并策略见 spec 第 10 节 */
    suspend fun import(jsonText: String): ImportSummary = db.withTransaction {
        val data = json.decodeFromString(BackupData.serializer(), jsonText)
        check(data.schemaVersion <= SCHEMA_VERSION) { "备份版本过新（${data.schemaVersion}），请先升级 app" }

        val referencedExerciseIdx = data.sets.map { it.exerciseIndex }.toSet()
        val exerciseIds = data.exercises.mapIndexed { idx, eb ->
            val existing = exerciseDao.findByName(eb.name)
            val id = existing?.id
                ?: exerciseDao.insert(Exercise(name = eb.name, createdAt = eb.createdAt))
            if (existing != null && existing.isArchived && idx in referencedExerciseIdx) {
                exerciseDao.setArchived(id, false)
            }
            id
        }

        data.weights.forEach { w ->
            upsertWeightByDate(
                weightDao,
                com.wc.workout.data.local.WeightRecord(
                    dateEpochDay = w.dateEpochDay, weightKg = w.weightKg, recordedAt = w.recordedAt
                )
            )
        }

        val sessionIds = data.sessions.map { s ->
            workoutDao.insertSession(
                WorkoutSession(title = s.title, startTime = s.startTime, endTime = s.endTime)
            )
        }

        data.sets.forEach { sb ->
            require(sb.sessionIndex in sessionIds.indices) { "备份文件损坏：sessionIndex 越界" }
            require(sb.exerciseIndex in exerciseIds.indices) { "备份文件损坏：exerciseIndex 越界" }
            workoutDao.insertSet(
                WorkoutSet(
                    sessionId = sessionIds[sb.sessionIndex],
                    exerciseId = exerciseIds[sb.exerciseIndex],
                    weightKg = sb.weightKg, reps = sb.reps,
                    exerciseOrder = sb.exerciseOrder, setOrder = sb.setOrder
                )
            )
        }

        ImportSummary(data.weights.size, data.exercises.size, data.sessions.size, data.sets.size)
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
```

- [ ] **Step 3: 启用 AppContainer 的 backupRepository**

把 `WorkoutApp.kt` 的 `AppContainer` 改为 Task 3 Step 3 所示的最终形态（去掉 `backupRepository` 行的注释/直接补上），并确认 import 列表包含 `WeightRepository`、`ExerciseRepository`、`WorkoutRepository`、`BackupRepository`。

- [ ] **Step 4: 写备份测试**

`app/src/test/java/com/wc/workout/data/repository/BackupTest.kt`:
```kotlin
package com.wc.workout.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() = db.close()

    private suspend fun seedSource(): String {
        val src = BackupRepository(db)
        val e = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 10))
        val s = db.workoutDao().insertSession(WorkoutSession(title = "推日", startTime = 100, endTime = 200))
        db.workoutDao().insertSet(
            com.wc.workout.data.local.WorkoutSet(
                sessionId = s, exerciseId = e, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1
            )
        )
        db.weightDao().insert(
            com.wc.workout.data.local.WeightRecord(dateEpochDay = 20_600, weightKg = 72.0, recordedAt = 111)
        )
        return src.export()
    }

    @Test
    fun exportImportRoundTripPreservesData() = runBlocking {
        val json = seedSource()
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val summary = BackupRepository(dest).import(json)

        assertEquals(1, summary.weights)
        assertEquals(1, summary.exercises)
        assertEquals(1, summary.sessions)
        assertEquals(1, summary.sets)

        assertEquals(72.0, dest.weightDao().getAll()[0].weightKg, 0.001)
        assertEquals("卧推", dest.exerciseDao().getAll()[0].name)
        val s = dest.workoutDao().getAllSessions()[0]
        assertEquals("推日", s.title); assertEquals(100L, s.startTime); assertEquals(200L, s.endTime)
        val sets = dest.workoutDao().getSetsWithExerciseNames(s.id)
        assertEquals(1, sets.size)
        assertEquals("卧推", sets[0].exerciseName)
        assertEquals(60.0, sets[0].set.weightKg, 0.001)
        dest.close()
    }

    @Test
    fun importOverwritesSameDayWeight() = runBlocking {
        val json = seedSource() // 备份里 day=20600 → 72.0
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dest.weightDao().insert(
            com.wc.workout.data.local.WeightRecord(dateEpochDay = 20_600, weightKg = 65.0, recordedAt = 999)
        )
        BackupRepository(dest).import(json)
        val rows = dest.weightDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(72.0, rows[0].weightKg, 0.001)
        assertEquals(111L, rows[0].recordedAt)
        dest.close()
    }

    @Test
    fun importUnarchivesReferencedExercise() = runBlocking {
        val json = seedSource()
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val id = dest.exerciseDao().insert(
            Exercise(name = "卧推", createdAt = 1, isArchived = true)
        )
        BackupRepository(dest).import(json)
        assertFalse(dest.exerciseDao().getById(id)!!.isArchived)
        dest.close()
    }

    @Test
    fun importRejectsNewerSchemaVersion() {
        val newer = """{"schemaVersion":99,"exportedAt":1,"weights":[],"exercises":[],"sessions":[],"sets":[]}"""
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BackupRepository(db).import(newer) }
        }
    }

    @Test
    fun importRejectsMalformedJson() {
        assertThrows(SerializationException::class.java) {
            runBlocking { BackupRepository(db).import("not a json") }
        }
    }
}
```

- [ ] **Step 5: 运行全部测试**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，22 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bat
git add -A
git commit -m "feat: json backup export/import with merge strategy and tests"
```

---

### Task 5: 应用框架（底部导航）+ 训练页体重卡片

**Files:**
- Create: `app/src/main/java/com/wc/workout/ui/WorkoutRoot.kt`、`ui/common/VM.kt`、`ui/common/Format.kt`、`ui/common/WeightEditDialog.kt`、`ui/home/HomeScreen.kt`、`ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/wc/workout/MainActivity.kt`
- Modify: `app/build.gradle.kts`（补充占位页面的依赖已齐备，无需改动；此处仅确认）

**Interfaces:**
- Consumes: `AppContainer`、`WeightRepository`、`WorkoutRepository`
- Produces:
  - `@Composable fun WorkoutRoot(container: AppContainer)`（含 4 tab 导航与 `workout/{sessionId}` 路由，先指向占位页）
  - `@Composable fun appContainer(): AppContainer`（ui/common/VM.kt，从 ApplicationContext 取容器）
  - `fun <VM : ViewModel> viewModelWith(creator: () -> VM): VM`
  - `formatTime(millis: Long): String`（HH:mm）、`formatDuration(seconds: Long): String`（m:ss / h:mm:ss）、`Double.kgLabel(): String`（固定 1 位小数）、`Double.displayKg(): String`（整数不带小数）
  - `@Composable fun WeightEditDialog(initialKg: Double?, onSaved: (String) -> Unit, onDismiss: () -> Unit)`（内部完成校验，只在合法时回调）
  - 路由名：`"home"`、`"calendar"`、`"library"`、`"trend"`、`"workout/{sessionId}"`（Long 参数）

- [ ] **Step 1: 写公共组件**

`app/src/main/java/com/wc/workout/ui/common/VM.kt`:
```kotlin
package com.wc.workout.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wc.workout.WorkoutApp
import com.wc.workout.AppContainer

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as WorkoutApp).container

class VMFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

@Composable
inline fun <reified VM : ViewModel> viewModelWith(noinline creator: () -> VM): VM =
    viewModel(factory = VMFactory(creator))
```

`app/src/main/java/com/wc/workout/ui/common/Format.kt`:
```kotlin
package com.wc.workout.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(hm)

/** 59 → "0:59"；3661 → "1:01:01" */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** 展示用：固定 1 位小数，如 "72.5" */
fun Double.kgLabel(): String = String.format(Locale.US, "%.1f", this)

/** 输入框预填用：整数不带小数，如 "60" */
fun Double.displayKg(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
```

`app/src/main/java/com/wc/workout/ui/common/WeightEditDialog.kt`:
```kotlin
package com.wc.workout.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun WeightEditDialog(
    initialKg: Double?,
    onSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialKg?.displayKg() ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialKg == null) "记录体重" else "修改体重") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    label = { Text("体重 (kg)") },
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (error) Text(
                    "请输入大于 0 的数字",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = text.toDoubleOrNull()
                if (kg == null || kg <= 0.0) error = true else { onSaved(text); onDismiss() }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
```

- [ ] **Step 2: 写 HomeViewModel 与 HomeScreen（本任务只含今日体重卡片）**

`app/src/main/java/com/wc/workout/ui/home/HomeViewModel.kt`:
```kotlin
package com.wc.workout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val weightRepo: WeightRepository,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val todayWeight: StateFlow<WeightRecord?> = weightRepo.observeBetween(today, today)
        .map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveWeight(kgText: String) {
        val kg = kgText.toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        viewModelScope.launch { weightRepo.saveWeight(today, kg) }
    }

    /** Task 9 会扩展：开始健身、进行中会话 */
    suspend fun recentTitles(): List<String> = workoutRepo.recentTitles()

    fun defaultTitle(): String =
        today.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 训练"
}
```

`app/src/main/java/com/wc/workout/ui/home/HomeScreen.kt`:
```kotlin
package com.wc.workout.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.ui.common.WeightEditDialog
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith

@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModelWith {
        HomeViewModel(container.weightRepository, container.workoutRepository)
    }
    val weight by vm.todayWeight.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("训练", style = MaterialTheme.typography.headlineMedium)
        TodayWeightCard(weight = weight, onSave = vm::saveWeight)
    }
}

@Composable
private fun TodayWeightCard(weight: WeightRecord?, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今日体重", style = MaterialTheme.typography.titleMedium)
            if (weight == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; showError = false },
                        label = { Text("kg") },
                        isError = showError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val kg = text.toDoubleOrNull()
                        if (kg == null || kg <= 0.0) showError = true else onSave(text)
                    }) { Text("记录") }
                }
                if (showError) Text(
                    "请输入大于 0 的数字",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("${weight.weightKg.kgLabel()} kg", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "记录于 ${formatTime(weight.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { editing = true }) { Text("修改") }
            }
        }
    }

    if (editing && weight != null) {
        WeightEditDialog(
            initialKg = weight!!.weightKg,
            onSaved = { onSave(it) },
            onDismiss = { editing = false }
        )
    }
}
```
（`weight!!` 的空判定由外层 `editing && weight != null` 保证；执行者若遇智能转换告警，可先 `val w = weight` 再使用。）

- [ ] **Step 3: 写 WorkoutRoot（底部导航）并接回 MainActivity**

`app/src/main/java/com/wc/workout/ui/WorkoutRoot.kt`:
```kotlin
package com.wc.workout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wc.workout.AppContainer
import com.wc.workout.ui.calendar.CalendarScreen
import com.wc.workout.ui.home.HomeScreen
import com.wc.workout.ui.library.ExerciseLibraryScreen
import com.wc.workout.ui.trend.TrendScreen

private data class BottomTab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun WorkoutRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        BottomTab("home", "训练") { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
        BottomTab("calendar", "日历") { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
        BottomTab("library", "动作库") { Icon(Icons.Outlined.SportsGymnastics, contentDescription = null) },
        BottomTab("trend", "趋势") { Icon(Icons.Filled.ShowChart, contentDescription = null) },
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(container)
            }
            composable("calendar") { CalendarScreen(container) }
            composable("library") { ExerciseLibraryScreen(container) }
            composable("trend") { TrendScreen(container) }
            composable(
                route = "workout/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                WorkoutSessionPlaceholder(sessionId)
            }
        }
    }
}

/** Task 9 会替换为真实训练页 */
@Composable
private fun WorkoutSessionPlaceholder(sessionId: Long) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("训练进行中（sessionId=$sessionId）")
    }
}
```
（`Icons.Outlined.SportsGymnastics` 来自 material-icons-extended（已引入）。）

`MainActivity.kt` 的 `setContent` 改为：
```kotlin
setContent {
    WorkoutTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val container = (applicationContext as WorkoutApp).container
            WorkoutRoot(container)
        }
    }
}
```
（import 增加 `com.wc.workout.ui.WorkoutRoot`；原占位 `Text` 删除。）

- [ ] **Step 4: 为其余三个 tab 写临时占位页（Task 6/7/8 会整体替换）**

在每个对应包下先创建最小占位（例如 `ui/calendar/CalendarScreen.kt`）:
```kotlin
package com.wc.workout.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wc.workout.AppContainer

@Composable
fun CalendarScreen(container: AppContainer) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("日历") }
}
```
同法创建 `ui/library/ExerciseLibraryScreen.kt`（文案"动作库"）与 `ui/trend/TrendScreen.kt`（文案"趋势"），包名与函数名保持一致（后续任务将原文件整段重写）。

- [ ] **Step 5: 构建并在模拟器/设备上验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

验收（有模拟器时执行）：
1. `gradlew.bat installDebug` 后启动 `com.wc.workout/.MainActivity`。
2. 核对：底部 4 个 tab 可切换；训练页录入今日体重（输入非法值被拒绝、合法值保存后卡片显示数值与时间）；点"修改"弹窗改值后立即更新。

- [ ] **Step 6: 提交**

```bat
git add -A
git commit -m "feat: bottom nav scaffold and today-weight card on home"
```

---

### Task 6: 日历页（月历网格 + 体重显示 + 当日详情基础）

**Files:**
- Modify（整文件重写）: `app/src/main/java/com/wc/workout/ui/calendar/CalendarScreen.kt`
- Create: `app/src/main/java/com/wc/workout/ui/calendar/CalendarViewModel.kt`

**Interfaces:**
- Consumes: Task 3 仓库、Task 5 公共组件（`viewModelWith`、`WeightEditDialog`、`formatTime`、`kgLabel`）
- Produces:
  - `class CalendarViewModel(weightRepo, workoutRepo)`：`month: StateFlow<YearMonth>`；`selectedDate: StateFlow<LocalDate?>`；
    `weightsForMonth: StateFlow<Map<Long, WeightRecord>>`；`sessionsForMonth: StateFlow<List<WorkoutSession>>`；
    `selectDay(date: LocalDate?)`；`prevMonth()`；`nextMonth()`；`goToday()`；
    `weightFor(date: LocalDate): WeightRecord?`；`sessionsFor(date: LocalDate): List<WorkoutSession>`；`saveWeight(date: LocalDate, kgText: String)`
  - `startOfDayMillis(d: LocalDate): Long` 与 `endOfDayMillisExclusive(d: LocalDate): Long`（本任务定义在 `ui/common/Format.kt` 中，Task 11 复用）
  - 日历格子规格：周一起始；当天高亮圆底；格子内第二行显示体重 kg（1 位小数）；第三行最多 3 个健身圆点

- [ ] **Step 1: 扩展 Format.kt（文件末尾追加）**

```kotlin
/** 当天 00:00 的 epoch millis（本地时区） */
fun startOfDayMillis(d: LocalDate): Long =
    d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 次日 00:00 的 epoch millis（本地时区），用于 BETWEEN 的上界 */
fun endOfDayMillisExclusive(d: LocalDate): Long =
    d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
```
（需要 `import java.time.LocalDate`；`ZoneId` 已有。）

- [ ] **Step 2: 写 CalendarViewModel**

`app/src/main/java/com/wc/workout/ui/calendar/CalendarViewModel.kt`:
```kotlin
package com.wc.workout.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository
import com.wc.workout.ui.common.endOfDayMillisExclusive
import com.wc.workout.ui.common.startOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val weightRepo: WeightRepository,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val weightsForMonth: StateFlow<Map<Long, WeightRecord>> = _month
        .flatMapLatest { m -> weightRepo.observeBetween(m.atDay(1), m.atEndOfMonth()) }
        .map { list -> list.associateBy { it.dateEpochDay } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val sessionsForMonth: StateFlow<List<WorkoutSession>> = _month
        .flatMapLatest { m ->
            workoutRepo.observeSessionsBetween(
                startOfDayMillis(m.atDay(1)),
                endOfDayMillisExclusive(m.atEndOfMonth())
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(date: LocalDate?) { _selectedDate.value = date }
    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun goToday() { _month.value = YearMonth.now(); _selectedDate.value = LocalDate.now() }

    suspend fun weightFor(date: LocalDate): WeightRecord? = weightRepo.getByDate(date)

    suspend fun sessionsFor(date: LocalDate): List<WorkoutSession> =
        workoutRepo.getSessionsBetween(startOfDayMillis(date), endOfDayMillisExclusive(date))

    fun saveWeight(date: LocalDate, kgText: String) {
        val kg = kgText.toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        viewModelScope.launch { weightRepo.saveWeight(date, kg) }
    }
}
```

- [ ] **Step 3: 整文件重写 CalendarScreen**

`app/src/main/java/com/wc/workout/ui/calendar/CalendarScreen.kt`:
```kotlin
package com.wc.workout.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.common.WeightEditDialog
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(container: AppContainer) {
    val vm: CalendarViewModel = viewModelWith {
        CalendarViewModel(container.weightRepository, container.workoutRepository)
    }
    val month by vm.month.collectAsState()
    val weights by vm.weightsForMonth.collectAsState()
    val sessions by vm.sessionsForMonth.collectAsState()
    val selected by vm.selectedDate.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上月")
            }
            Text(
                "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下月")
            }
            TextButton(onClick = vm::goToday) { Text("今") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        MonthGrid(month, weights, sessions, onDayClick = vm::selectDay, modifier = Modifier.fillMaxWidth())
    }

    selected?.let { date ->
        DayDetailSheet(date, vm, onDismiss = { vm.selectDay(null) })
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    weights: Map<Long, WeightRecord>,
    sessions: List<WorkoutSession>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // 周一起始：周一偏移 0，周日偏移 6
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val zone = ZoneId.systemDefault()
    val sessionsByDay = sessions.groupBy {
        Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
    }

    Column(modifier) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val index = r * 7 + c
                    val date = first.minusDays(offset.toLong()).plusDays(index.toLong())
                    if (date.month == month.month) {
                        DayCell(
                            date = date,
                            weight = weights[date.toEpochDay()],
                            daySessions = sessionsByDay[date].orEmpty(),
                            modifier = Modifier.weight(1f),
                            onDayClick = onDayClick
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    weight: WeightRecord?,
    daySessions: List<WorkoutSession>,
    modifier: Modifier,
    onDayClick: (LocalDate) -> Unit
) {
    val isToday = date == LocalDate.now()
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onDayClick(date) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            weight?.weightKg?.kgLabel() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            daySessions.take(3).forEach {
                Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(date: LocalDate, vm: CalendarViewModel, onDismiss: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val dayWeight by produceState<WeightRecord?>(initialValue = null, date, refresh) {
        value = vm.weightFor(date)
    }
    val daySessions by produceState<List<WorkoutSession>>(initialValue = emptyList(), date, refresh) {
        value = vm.sessionsFor(date)
    }
    var showWeightDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${date.monthValue}月${date.dayOfMonth}日", style = MaterialTheme.typography.titleLarge)

            // —— 体重 ——
            if (dayWeight == null) {
                TextButton(onClick = { showWeightDialog = true }) { Text("记录体重") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "体重 ${dayWeight!!.weightKg.kgLabel()} kg（${formatTime(dayWeight!!.recordedAt)} 记录）",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showWeightDialog = true }) { Text("修改") }
                }
            }

            // —— 健身记录 ——
            Text("健身记录", style = MaterialTheme.typography.titleMedium)
            if (daySessions.isEmpty()) {
                Text(
                    "这一天没有健身记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                daySessions.forEach { session ->
                    Text(
                        "${session.title}（${formatTime(session.startTime)} 开始）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showWeightDialog) {
        WeightEditDialog(
            initialKg = dayWeight?.weightKg,
            onSaved = { vm.saveWeight(date, it); refresh++ },
            onDismiss = { showWeightDialog = false }
        )
    }
}
```
（当月格子总数不足 6 行时按实际行数渲染；空格子显示为 Spacer。）

- [ ] **Step 4: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

模拟器验收：切换到日历 tab → 本月网格正确（周一起始、当天高亮）；点击有体重的那天 → BottomSheet 显示体重并可"修改"覆盖；左右箭头切换月份、"今"回到当月。

- [ ] **Step 5: 提交**

```bat
git add -A
git commit -m "feat: month calendar grid with weight per day and day detail sheet"
```

---

### Task 7: 趋势页（折线图 + 摘要）

**Files:**
- Modify（整文件重写）: `app/src/main/java/com/wc/workout/ui/trend/TrendScreen.kt`
- Create: `app/src/main/java/com/wc/workout/ui/trend/TrendViewModel.kt`、`app/src/main/java/com/wc/workout/ui/trend/WeightLineChart.kt`

**Interfaces:**
- Consumes: `WeightRepository.observeAll()`
- Produces:
  - `enum class TrendRange(val label: String, val days: Int?) { D30("近30天", 30), D90("近90天", 90), ALL("全部", null) }`
  - `@Composable fun WeightLineChart(points: List<Pair<Long, Double>>, modifier: Modifier)`（x=epochDay 升序，y=kg；<2 个点时画单点）

- [ ] **Step 1: 写 TrendViewModel 与折线图**

`app/src/main/java/com/wc/workout/ui/trend/TrendViewModel.kt`:
```kotlin
package com.wc.workout.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

enum class TrendRange(val label: String, val days: Int?) {
    D30("近30天", 30), D90("近90天", 90), ALL("全部", null)
}

class TrendViewModel(weightRepo: WeightRepository) : ViewModel() {
    val weights: StateFlow<List<WeightRecord>> = weightRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val range = MutableStateFlow(TrendRange.D30)
}
```

`app/src/main/java/com/wc/workout/ui/trend/WeightLineChart.kt`:
```kotlin
package com.wc.workout.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun WeightLineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val pad = 24f
        val paint = android.graphics.Paint().apply {
            textSize = 28f
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }
        if (points.size == 1) {
            drawCircle(lineColor, radius = 8f, center = Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val minD = points.first().first.toFloat()
        val maxD = points.last().first.toFloat()
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val yLo = (minV - 1).toFloat()
        val yHi = (maxV + 1).toFloat()

        fun px(day: Long): Float =
            pad + (size.width - 2 * pad) * ((day.toFloat() - minD) / ((maxD - minD).coerceAtLeast(1f)))

        fun py(v: Double): Float =
            size.height - pad - (size.height - 2 * pad) * ((v.toFloat() - yLo) / (yHi - yLo))

        val path = Path()
        points.forEachIndexed { i, (day, kg) ->
            val x = px(day); val y = py(kg)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (points.size <= 90) {
            points.forEach { (day, kg) ->
                drawCircle(lineColor, radius = 4.dp.toPx() / 2, center = Offset(px(day), py(kg)))
            }
        }
        drawContext.canvas.nativeCanvas.drawText(
            "%.1f".format(maxV), pad, py(maxV) + 28f, paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "%.1f".format(minV), pad, py(minV), paint
        )
    }
}
```
（`"%.1f".format(...)` 若报 Locale 告警可改 `String.format(Locale.US, ...)`。）

- [ ] **Step 2: 整文件重写 TrendScreen**

`app/src/main/java/com/wc/workout/ui/trend/TrendScreen.kt`:
```kotlin
package com.wc.workout.ui.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TrendScreen(container: AppContainer) {
    val vm: TrendViewModel = viewModelWith { TrendViewModel(container.weightRepository) }
    val all by vm.weights.collectAsState()
    val range by vm.range.collectAsState()

    val today = LocalDate.now().toEpochDay()
    val shown = remember(all, range, today) {
        when (val r = range) {
            TrendRange.ALL -> all
            else -> all.filter { it.dateEpochDay >= today - (r.days ?: 0) + 1 }
        }
    }
    val values = shown.map { it.weightKg }
    val zone = ZoneId.systemDefault()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd")

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("体重趋势", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { r ->
                FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
            }
        }
        if (shown.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center
            ) { Text("这个范围内还没有体重记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            WeightLineChart(
                points = shown.map { it.dateEpochDay to it.weightKg },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Instant.ofEpochMilli(shown.first().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                Text(Instant.ofEpochMilli(shown.last().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最高 ${values.max().kgLabel()}")
                Text("最低 ${values.min().kgLabel()}")
                Text("平均 ${values.average().kgLabel()}")
            }
        }
    }
}
```

- [ ] **Step 3: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

模拟器验收：趋势 tab 显示范围切换；有多天体重数据时折线与最高/最低/平均正确（可先在训练页连续改几天日期？——不行，训练页只写今天。直接用"记录体重 + 修改手机系统日期"验证，或接受仅当日单点显示）。

- [ ] **Step 4: 提交**

```bat
git add -A
git commit -m "feat: weight trend line chart with range filter and summary"
```

---

### Task 8: 动作库页

**Files:**
- Modify（整文件重写）: `app/src/main/java/com/wc/workout/ui/library/ExerciseLibraryScreen.kt`
- Create: `app/src/main/java/com/wc/workout/ui/library/ExerciseLibraryViewModel.kt`、`app/src/main/java/com/wc/workout/ui/common/NameDialog.kt`

**Interfaces:**
- Consumes: `ExerciseRepository`、Task 5 `viewModelWith`
- Produces:
  - `@Composable fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: suspend (String) -> String?, onDismiss: () -> Unit)`
    （`onConfirm` 返回 `null` 表示成功并自动关闭，返回字符串则作为错误显示并保持打开）
  - `class ExerciseLibraryViewModel(repo)`：`query: MutableStateFlow<String>`；`exercises` / `archived: StateFlow<List<Exercise>>`；
    `add(name): ExerciseNameResult`；`rename(id, name): ExerciseNameResult`；`archive(id)`；`unarchive(id)`；`delete(id): Boolean`；`isReferenced(id): Boolean`

- [ ] **Step 1: 写 NameDialog 公共组件**

`app/src/main/java/com/wc/workout/ui/common/NameDialog.kt`:
```kotlin
package com.wc.workout.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: suspend (String) -> String?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    isError = error != null,
                    singleLine = true
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "名称不能为空"
                } else {
                    scope.launch {
                        val err = onConfirm(name.trim())
                        if (err == null) onDismiss() else error = err
                    }
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
```

- [ ] **Step 2: 写 ExerciseLibraryViewModel**

`app/src/main/java/com/wc/workout/ui/library/ExerciseLibraryViewModel.kt`:
```kotlin
package com.wc.workout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ExerciseLibraryViewModel(private val repo: ExerciseRepository) : ViewModel() {

    val query = MutableStateFlow("")

    val exercises: StateFlow<List<Exercise>> =
        combine(repo.observeActive(), query) { list, q ->
            if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<Exercise>> = repo.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(name: String): ExerciseNameResult = repo.addExercise(name)
    suspend fun rename(id: Long, name: String): ExerciseNameResult = repo.rename(id, name)
    suspend fun archive(id: Long) = repo.setArchived(id, true)
    suspend fun unarchive(id: Long) = repo.setArchived(id, false)
    suspend fun delete(id: Long): Boolean = repo.tryDelete(id)
    suspend fun isReferenced(id: Long): Boolean = repo.isReferenced(id)
}
```

- [ ] **Step 3: 整文件重写 ExerciseLibraryScreen**

`app/src/main/java/com/wc/workout/ui/library/ExerciseLibraryScreen.kt`:
```kotlin
package com.wc.workout.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.ui.common.NameDialog
import com.wc.workout.ui.common.viewModelWith
import kotlinx.coroutines.launch

@Composable
fun ExerciseLibraryScreen(container: AppContainer) {
    val vm: ExerciseLibraryViewModel = viewModelWith { ExerciseLibraryViewModel(container.exerciseRepository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val query by vm.query.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val archived by vm.archived.collectAsState()
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Exercise?>(null) }
    var menuTarget by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("动作库", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = { Text("搜索动作") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("新建动作") }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (exercises.isEmpty() && query.isBlank()) {
                    item {
                        Text(
                            "还没有动作，点「新建动作」添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(exercises, key = { it.id }) { ex ->
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        trailingContent = {
                            IconButton(onClick = { menuTarget = ex }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                        },
                        modifier = Modifier.clickable { renameTarget = ex }
                    )
                }
                if (archived.isNotEmpty()) {
                    item {
                        TextButton(onClick = { archivedExpanded = !archivedExpanded }) {
                            Text(if (archivedExpanded) "收起已归档 (${archived.size})" else "已归档 (${archived.size})")
                        }
                    }
                }
                if (archivedExpanded) {
                    items(archived, key = { "archived-${it.id}" }) { ex ->
                        ListItem(
                            headlineContent = {
                                Text(ex.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = {
                                TextButton(onClick = { scope.launch { vm.unarchive(ex.id) } }) { Text("取消归档") }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = "新建动作",
            initial = query,
            confirmLabel = "创建",
            onConfirm = { name ->
                if (vm.add(name) is ExerciseNameResult.Duplicate) "已存在同名动作" else null
            },
            onDismiss = { showAdd = false }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "重命名动作",
            initial = target.name,
            confirmLabel = "保存",
            onConfirm = { name ->
                if (vm.rename(target.id, name) is ExerciseNameResult.Duplicate) "已存在同名动作" else null
            },
            onDismiss = { renameTarget = null }
        )
    }

    menuTarget?.let { target ->
        val canDelete by produceState(initialValue = false, target.id) {
            value = !vm.isReferenced(target.id)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = { renameTarget = target; menuTarget = null })
                DropdownMenuItem(text = { Text("归档") }, onClick = { scope.launch { vm.archive(target.id) }; menuTarget = null })
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            scope.launch {
                                if (vm.delete(target.id)) snackbar.showSnackbar("已删除「${target.name}」")
                            }
                            menuTarget = null
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

模拟器验收：新建两个动作；重名被拒并提示；重命名；搜索过滤；新建一次训练并录组（该动作被引用）后回到动作库 → 对该动作的菜单里**没有"删除"只有"归档"**；归档后从主列表消失、出现在"已归档"区、可取消归档。

- [ ] **Step 5: 提交**

```bat
git add -A
git commit -m "feat: exercise library with search, rename, archive and delete rules"
```

---

### Task 9: 健身会话：创建流程、计时器、结束/放弃

**Files:**
- Create: `app/src/main/java/com/wc/workout/ui/workout/WorkoutSessionScreen.kt`（本任务版本：计时器 + 结束/放弃 + 空状态）、`ui/workout/WorkoutSessionViewModel.kt`、`ui/home/StartWorkoutDialog.kt`
- Modify: `app/src/main/java/com/wc/workout/ui/home/HomeScreen.kt`（追加开始按钮/进行中卡片）、`ui/home/HomeViewModel.kt`、`app/src/main/java/com/wc/workout/ui/WorkoutRoot.kt`（占位页换真实页）

**Interfaces:**
- Consumes: Task 3 `WorkoutRepository`、Task 5 `formatTime/formatDuration`、Task 8 `ElapsedTimer`（本任务创建）
- Produces:
  - `@Composable fun WorkoutSessionScreen(container: AppContainer, sessionId: Long, onFinished: () -> Unit)`
  - `@Composable fun ElapsedTimer(startTime: Long, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium)`（公开，Home 复用）
  - `@Composable fun StartWorkoutDialog(onLoadTitles: suspend () -> List<String>, onConfirm: (String) -> Unit, onDismiss: () -> Unit)`、`@Composable fun OngoingCard(session: WorkoutSession, onClick: () -> Unit)`
  - `HomeScreen(container: AppContainer, onStartWorkout: (Long) -> Unit)`（签名变化）
  - `HomeViewModel.ongoingSession: StateFlow<WorkoutSession?>`、`HomeViewModel.startSession(title: String, onStarted: (Long) -> Unit)`

- [ ] **Step 1: WorkoutSessionViewModel（本任务版本）**

`app/src/main/java/com/wc/workout/ui/workout/WorkoutSessionViewModel.kt`:
```kotlin
package com.wc.workout.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WorkoutSessionViewModel(
    private val workoutRepo: WorkoutRepository,
    private val sessionId: Long,
) : ViewModel() {

    val session: StateFlow<WorkoutSession?> = flow { emit(workoutRepo.getSession(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val reload = MutableStateFlow(0)

    val groups: StateFlow<List<SetWithExercise>> = reload
        .map { workoutRepo.getSetsWithExerciseNames(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() { reload.value++ }

    suspend fun endSession() = workoutRepo.endSession(sessionId)
    suspend fun abandon() = workoutRepo.abandonSession(sessionId)
}
```

- [ ] **Step 2: WorkoutSessionScreen（本任务版本）**

`app/src/main/java/com/wc/workout/ui/workout/WorkoutSessionScreen.kt`:
```kotlin
package com.wc.workout.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkoutSessionScreen(container: AppContainer, sessionId: Long, onFinished: () -> Unit) {
    val vm: WorkoutSessionViewModel = viewModelWith {
        WorkoutSessionViewModel(container.workoutRepository, sessionId)
    }
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()
    val groups by vm.groups.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge)
        ElapsedTimer(startTime = s.startTime, style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAbandonDialog = true }, modifier = Modifier.weight(1f)) { Text("放弃") }
            Button(onClick = { showEndDialog = true }, modifier = Modifier.weight(1f)) { Text("结束健身") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("还没有记录任何动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("结束健身") },
            text = { Text(if (groups.isEmpty()) "本次还没有记录任何组，确定结束吗？" else "结束并保存本次训练？") },
            confirmButton = {
                TextButton(onClick = { scope.launch { vm.endSession(); onFinished() } }) { Text("结束") }
            },
            dismissButton = { TextButton(onClick = { showEndDialog = false }) { Text("继续训练") } }
        )
    }
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("放弃本次训练") },
            text = { Text("将删除本次训练及其全部组记录，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = { scope.launch { vm.abandon(); onFinished() } }) { Text("放弃") }
            },
            dismissButton = { TextButton(onClick = { showAbandonDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun ElapsedTimer(
    startTime: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        formatDuration((now - startTime).coerceAtLeast(0) / 1000),
        style = style,
        modifier = modifier
    )
}
```
（`viewModelWith` 来自 `com.wc.workout.ui.common.viewModelWith`，补 import。）

- [ ] **Step 3: StartWorkoutDialog 与 OngoingCard**

`app/src/main/java/com/wc/workout/ui/home/StartWorkoutDialog.kt`:
```kotlin
package com.wc.workout.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.workout.ElapsedTimer

@Composable
fun StartWorkoutDialog(
    onLoadTitles: suspend () -> List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { titles = onLoadTitles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始健身") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（留空自动用日期）") },
                    singleLine = true
                )
                if (titles.isNotEmpty()) {
                    Text(
                        "最近使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    titles.take(5).forEach { t ->
                        TextButton(onClick = { title = t }) { Text(t) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title) }) { Text("开始") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun OngoingCard(session: WorkoutSession, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                ElapsedTimer(session.startTime, style = MaterialTheme.typography.headlineSmall)
            }
            Text("继续 ›", color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

- [ ] **Step 4: 扩展 HomeViewModel 与 HomeScreen**

`HomeViewModel.kt` 在类体内追加（`WorkoutSession` 需要 `import com.wc.workout.data.local.WorkoutSession`）:
```kotlin
val ongoingSession: StateFlow<WorkoutSession?> = workoutRepo.observeOngoing()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

fun startSession(title: String, onStarted: (Long) -> Unit) {
    viewModelScope.launch {
        val finalTitle = title.ifBlank { defaultTitle() }
        onStarted(workoutRepo.startSession(finalTitle))
    }
}
```

`HomeScreen.kt`：`HomeScreen` 可组合函数整体替换为（`TodayWeightCard` 保持不变）:
```kotlin
@Composable
fun HomeScreen(container: AppContainer, onStartWorkout: (Long) -> Unit) {
    val vm: HomeViewModel = viewModelWith {
        HomeViewModel(container.weightRepository, container.workoutRepository)
    }
    val weight by vm.todayWeight.collectAsState()
    val ongoing by vm.ongoingSession.collectAsState()
    var showTitleDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("训练", style = MaterialTheme.typography.headlineMedium)
        TodayWeightCard(weight = weight, onSave = vm::saveWeight)

        val current = ongoing
        if (current != null) {
            OngoingCard(current) { onStartWorkout(current.id) }
        } else {
            Button(onClick = { showTitleDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("开始健身")
            }
        }
    }

    if (showTitleDialog) {
        StartWorkoutDialog(
            onLoadTitles = { vm.recentTitles() },
            onConfirm = { title ->
                showTitleDialog = false
                vm.startSession(title, onStarted = onStartWorkout)
            },
            onDismiss = { showTitleDialog = false }
        )
    }
}
```
（追加 import：`com.wc.workout.ui.workout` 无需——`StartWorkoutDialog`/`OngoingCard` 同包；确保 `Button` 已 import。）

- [ ] **Step 5: WorkoutRoot 接入真实训练页**

`WorkoutRoot.kt` 两处修改：
```kotlin
composable("home") {
    HomeScreen(container, onStartWorkout = { id -> navController.navigate("workout/$id") })
}
```
以及把 `workout/{sessionId}` 的内容替换、并删除 `WorkoutSessionPlaceholder`:
```kotlin
composable(
    route = "workout/{sessionId}",
    arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
) { entry ->
    val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
    WorkoutSessionScreen(container, sessionId, onFinished = { navController.popBackStack() })
}
```

- [ ] **Step 6: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

模拟器验收：训练页"开始健身"→ 标题弹窗（空标题以日期兜底、历史标题可点选）→ 进入训练页计时器每秒走动；杀掉 app 重开 → 首页出现进行中卡片且计时正确（startTime 落库生效）；"结束健身"后卡片消失；再开一次并"放弃" → 日历与数据中无痕。

- [ ] **Step 7: 提交**

```bat
git add -A
git commit -m "feat: workout session lifecycle with title dialog, timer, end and abandon"
```

---

### Task 10: 组录入：添加动作、录组、上次成绩参考、编辑

**Files:**
- Modify（整文件重写）: `app/src/main/java/com/wc/workout/ui/workout/WorkoutSessionScreen.kt`、`ui/workout/WorkoutSessionViewModel.kt`
- Create: `app/src/main/java/com/wc/workout/ui/workout/AddExerciseSheet.kt`、`app/src/main/java/com/wc/workout/ui/workout/SetDialogs.kt`

**Interfaces:**
- Consumes: `ExerciseRepository`、Task 8 `NameDialog`（未用到，动作创建走 Sheet）、`displayKg`
- Produces:
  - `class WorkoutSessionViewModel(workoutRepo, exerciseRepo, sessionId)`（构造函数**追加** `exerciseRepo`）
  - `data class ExerciseCardUi(exercise: Exercise, sets: List<WorkoutSet>, pending: Boolean)`
  - `@Composable fun AddExerciseSheet(vm: WorkoutSessionViewModel, onDismiss: () -> Unit)`
  - `@Composable fun SetEditDialog(set: WorkoutSet, onSaved: (Double, Int) -> Unit, onDeleted: () -> Unit, onDismiss: () -> Unit)`
  - 组录入交互：录入行预填上次第一组；保存后重量保留、次数清空；同一动作一张卡片

- [ ] **Step 1: WorkoutSessionViewModel（最终版，整文件重写）**

```kotlin
package com.wc.workout.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WorkoutSessionViewModel(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val sessionId: Long,
) : ViewModel() {

    val session: StateFlow<WorkoutSession?> = flow { emit(workoutRepo.getSession(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val reload = MutableStateFlow(0)

    val groups: StateFlow<List<SetWithExercise>> = reload
        .map { workoutRepo.getSetsWithExerciseNames(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<Exercise>> = exerciseRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exerciseQuery = MutableStateFlow("")

    val filteredExercises: StateFlow<List<Exercise>> =
        combine(exercises, exerciseQuery) { list, q ->
            if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已选但尚无组记录的动作（未持久化，仅本次页面生命周期） */
    val pendingExerciseIds = MutableStateFlow<List<Long>>(emptyList())

    fun refresh() { reload.value++ }

    /** 返回 false 表示该动作已有卡片（用于提示/滚动定位） */
    suspend fun addPendingExercise(exerciseId: Long): Boolean {
        if (groups.value.any { it.set.exerciseId == exerciseId }) return false
        if (exerciseId !in pendingExerciseIds.value) {
            pendingExerciseIds.value = pendingExerciseIds.value + exerciseId
        }
        return true
    }

    /** 新建动作入库；重名返回 null */
    suspend fun createExercise(name: String): Exercise? =
        when (val r = exerciseRepo.addExercise(name)) {
            is ExerciseNameResult.Success -> exerciseRepo.getById(r.id)
            ExerciseNameResult.Duplicate -> null
        }

    suspend fun addSet(exerciseId: Long, weightKg: Double, reps: Int) {
        workoutRepo.addSet(sessionId, exerciseId, weightKg, reps)
        refresh()
    }

    suspend fun updateSet(set: WorkoutSet) { workoutRepo.updateSet(set); refresh() }
    suspend fun deleteSet(id: Long) { workoutRepo.deleteSet(id); refresh() }
    suspend fun removeExercise(exerciseId: Long) {
        workoutRepo.removeExerciseFromSession(sessionId, exerciseId)
        refresh()
    }

    suspend fun lastPerformance(exerciseId: Long): List<WorkoutSet> =
        workoutRepo.lastPerformance(exerciseId, sessionId)

    suspend fun endSession() = workoutRepo.endSession(sessionId)
    suspend fun abandon() = workoutRepo.abandonSession(sessionId)
}
```

- [ ] **Step 2: AddExerciseSheet 与 SetEditDialog**

`app/src/main/java/com/wc/workout/ui/workout/AddExerciseSheet.kt`:
```kotlin
package com.wc.workout.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(vm: WorkoutSessionViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val query by vm.exerciseQuery.collectAsState()
    val list by vm.filteredExercises.collectAsState()
    var createError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("添加动作", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { vm.exerciseQuery.value = it; createError = false },
                label = { Text("搜索动作，或输入新名字新建") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val trimmed = query.trim()
            val exactExists = trimmed.isNotEmpty() && list.any { it.name.equals(trimmed, ignoreCase = true) }
            if (trimmed.isNotEmpty() && !exactExists) {
                if (createError) {
                    Text(
                        "已存在同名动作",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = {
                    scope.launch {
                        val created = vm.createExercise(trimmed)
                        if (created == null) {
                            createError = true
                        } else if (vm.addPendingExercise(created.id)) {
                            onDismiss()
                        }
                    }
                }) { Text("新建“$trimmed”并加入动作库") }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(list, key = { it.id }) { ex ->
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        modifier = Modifier.clickable {
                            scope.launch { if (vm.addPendingExercise(ex.id)) onDismiss() }
                        }
                    )
                }
            }
        }
    }
}
```

`app/src/main/java/com/wc/workout/ui/workout/SetDialogs.kt`:
```kotlin
package com.wc.workout.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.ui.common.displayKg

@Composable
fun SetEditDialog(
    set: WorkoutSet,
    onSaved: (Double, Int) -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf(set.weightKg.displayKg()) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    val valid = (weight.toDoubleOrNull()?.takeIf { it > 0.0 } != null) &&
        (reps.toIntOrNull()?.takeIf { it > 0 } != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 ${set.setOrder} 组") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("重量 kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = reps, onValueChange = { reps = it },
                    label = { Text("次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSaved(weight.toDouble(), reps.toInt()) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleted) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
```

- [ ] **Step 3: WorkoutSessionScreen（最终版，整文件重写）**

```kotlin
package com.wc.workout.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.viewModelWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 一张动作卡片：持久化的组 + 或尚无组的 pending 动作 */
data class ExerciseCardUi(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val pending: Boolean
)

@Composable
fun WorkoutSessionScreen(container: AppContainer, sessionId: Long, onFinished: () -> Unit) {
    val vm: WorkoutSessionViewModel = viewModelWith {
        WorkoutSessionViewModel(container.workoutRepository, container.exerciseRepository, sessionId)
    }
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()
    val groups by vm.groups.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val pending by vm.pendingExerciseIds.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<WorkoutSet?>(null) }
    var removingCard by remember { mutableStateOf<ExerciseCardUi?>(null) }

    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val cards = remember(groups, exercises, pending) {
        val persisted = groups.groupBy { it.set.exerciseId }.map { (exId, rows) ->
            ExerciseCardUi(
                exercise = Exercise(id = exId, name = rows.first().exerciseName, createdAt = 0),
                sets = rows.map { it.set },
                pending = false
            )
        }.sortedBy { card -> card.sets.minOf { it.exerciseOrder } }
        val persistedIds = persisted.map { it.exercise.id }.toSet()
        val pendingCards = pending.filter { it !in persistedIds }.mapNotNull { id ->
            exercises.firstOrNull { it.id == id }?.let { ExerciseCardUi(it, emptyList(), pending = true) }
        }
        persisted + pendingCards
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge)
        ElapsedTimer(startTime = s.startTime, style = MaterialTheme.typography.headlineMedium)

        if (cards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("点下方按钮添加第一个动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards, key = { it.exercise.id }) { card ->
                    ExerciseCard(
                        card = card,
                        onLoadLast = { vm.lastPerformance(card.exercise.id) },
                        onAddSet = { w, r -> scope.launch { vm.addSet(card.exercise.id, w, r) } },
                        onEditSet = { editingSet = it },
                        onRemove = { removingCard = card }
                    )
                }
            }
        }

        Button(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth()) { Text("添加动作") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAbandonDialog = true }, modifier = Modifier.weight(1f)) { Text("放弃") }
            Button(onClick = { showEndDialog = true }, modifier = Modifier.weight(1f)) { Text("结束健身") }
        }
    }

    if (showAddSheet) {
        AddExerciseSheet(vm = vm, onDismiss = { showAddSheet = false })
    }

    editingSet?.let { set ->
        SetEditDialog(
            set = set,
            onSaved = { w, r ->
                scope.launch { vm.updateSet(set.copy(weightKg = w, reps = r)) }
                editingSet = null
            },
            onDeleted = {
                scope.launch { vm.deleteSet(set.id) }
                editingSet = null
            },
            onDismiss = { editingSet = null }
        )
    }

    removingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { removingCard = null },
            title = { Text("移除动作") },
            text = { Text("删除「${card.exercise.name}」下的全部组记录？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.removeExercise(card.exercise.id) }
                    removingCard = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { removingCard = null }) { Text("取消") } }
        )
    }

    EndAndAbandonDialogs(
        groupsEmpty = groups.isEmpty(),
        showEnd = showEndDialog,
        onDismissEnd = { showEndDialog = false },
        onEnd = { scope.launch { vm.endSession(); onFinished() } },
        showAbandon = showAbandonDialog,
        onDismissAbandon = { showAbandonDialog = false },
        onAbandon = { scope.launch { vm.abandon(); onFinished() } }
    )
}

@Composable
private fun EndAndAbandonDialogs(
    groupsEmpty: Boolean,
    showEnd: Boolean,
    onDismissEnd: () -> Unit,
    onEnd: () -> Unit,
    showAbandon: Boolean,
    onDismissAbandon: () -> Unit,
    onAbandon: () -> Unit
) {
    if (showEnd) {
        AlertDialog(
            onDismissRequest = onDismissEnd,
            title = { Text("结束健身") },
            text = { Text(if (groupsEmpty) "本次还没有记录任何组，确定结束吗？" else "结束并保存本次训练？") },
            confirmButton = { TextButton(onClick = onEnd) { Text("结束") } },
            dismissButton = { TextButton(onClick = onDismissEnd) { Text("继续训练") } }
        )
    }
    if (showAbandon) {
        AlertDialog(
            onDismissRequest = onDismissAbandon,
            title = { Text("放弃本次训练") },
            text = { Text("将删除本次训练及其全部组记录，且不可恢复。") },
            confirmButton = { TextButton(onClick = onAbandon) { Text("放弃") } },
            dismissButton = { TextButton(onClick = onDismissAbandon) { Text("取消") } }
        )
    }
}

@Composable
private fun ExerciseCard(
    card: ExerciseCardUi,
    onLoadLast: suspend () -> List<WorkoutSet>,
    onAddSet: (Double, Int) -> Unit,
    onEditSet: (WorkoutSet) -> Unit,
    onRemove: () -> Unit
) {
    val last by produceState<List<WorkoutSet>>(emptyList(), card.exercise.id) {
        value = onLoadLast()
    }
    var weight by remember(card.exercise.id) { mutableStateOf("") }
    var reps by remember(card.exercise.id) { mutableStateOf("") }
    LaunchedEffect(last) {
        val first = last.firstOrNull()
        if (first != null && weight.isBlank() && reps.isBlank()) {
            weight = first.weightKg.displayKg()
            reps = first.reps.toString()
        }
    }
    val valid = (weight.toDoubleOrNull()?.takeIf { it > 0.0 } != null) &&
        (reps.toIntOrNull()?.takeIf { it > 0 } != null)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("移除") }
            }
            if (last.isNotEmpty()) {
                Text(
                    "上次：" + last.joinToString(", ") { "${it.weightKg.displayKg()}kg×${it.reps}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            card.sets.forEach { set ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEditSet(set) }.padding(vertical = 4.dp)
                ) {
                    Text("第 ${set.setOrder} 组", modifier = Modifier.weight(1f))
                    Text("${set.weightKg.displayKg()}kg × ${set.reps} 次")
                }
            }
            if (card.pending) {
                Text(
                    "将从第一组开始记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("重量 kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = reps, onValueChange = { reps = it },
                    label = { Text("次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onAddSet(weight.toDouble(), reps.toInt()); reps = "" },
                    enabled = valid
                ) { Text("添加") }
            }
        }
    }
}

@Composable
fun ElapsedTimer(
    startTime: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        formatDuration((now - startTime).coerceAtLeast(0) / 1000),
        style = style,
        modifier = modifier
    )
}
```

- [ ] **Step 4: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat testDebugUnitTest && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`（22 个测试 + 构建）。

模拟器验收（完整训练流程）：开始健身 → 添加动作（列表选择/搜索/新建）→ 给同一动作连续录两组（重量保留、次数清空、组序号递增）→ 添加第二个动作（exerciseOrder 正确）→ 点某组行可编辑/删除 → 第二次训练同动作显示"上次：…"且预填 → 结束 → 日历当天出现圆点。

- [ ] **Step 5: 提交**

```bat
git add -A
git commit -m "feat: per-exercise set logging with last performance reference"
```

---

### Task 11: 日历详情：会话卡片展开明细与删除

**Files:**
- Modify: `app/src/main/java/com/wc/workout/ui/calendar/CalendarScreen.kt`（DayDetailSheet 的会话区替换为 SessionCard）、`ui/calendar/CalendarViewModel.kt`（追加两个方法）

**Interfaces:**
- Consumes: Task 2 `SetWithExercise`、Task 5/6 `formatDuration`、`displayKg`、`formatTime`
- Produces: `CalendarViewModel.sessionDetail(sessionId: Long): List<SetWithExercise>`；`CalendarViewModel.deleteSession(id: Long)`

- [ ] **Step 1: CalendarViewModel 追加方法**

```kotlin
suspend fun sessionDetail(sessionId: Long): List<SetWithExercise> =
    workoutRepo.getSetsWithExerciseNames(sessionId)

suspend fun deleteSession(id: Long) = workoutRepo.abandonSession(id)
```
（import `com.wc.workout.data.local.SetWithExercise`。）

- [ ] **Step 2: DayDetailSheet 会话区替换**

`CalendarScreen.kt` 中把
```kotlin
            } else {
                daySessions.forEach { session ->
                    Text(
                        "${session.title}（${formatTime(session.startTime)} 开始）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
```
替换为
```kotlin
            } else {
                daySessions.forEach { session ->
                    SessionCard(session = session, vm = vm, onDeleted = { refresh++ })
                }
            }
```
并在文件末尾追加 `SessionCard`（同时补 import：`androidx.compose.material3.ElevatedCard`、`androidx.compose.material3.AlertDialog`、`com.wc.workout.data.local.SetWithExercise`、`com.wc.workout.ui.common.displayKg`、`com.wc.workout.ui.common.formatDuration`、`androidx.compose.runtime.rememberCoroutineScope`、`kotlinx.coroutines.launch`）:
```kotlin
@Composable
private fun SessionCard(session: WorkoutSession, vm: CalendarViewModel, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<List<SetWithExercise>>(emptyList()) }
    var showDelete by remember { mutableStateOf(false) }

    ElevatedCard(
        Modifier.fillMaxWidth().clickable {
            expanded = !expanded
            if (expanded && detail.isEmpty()) {
                scope.launch { detail = vm.sessionDetail(session.id) }
            }
        }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            val endText = session.endTime?.let { formatTime(it) } ?: "进行中"
            val durationSec = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000
            Text(
                "${formatTime(session.startTime)} – $endText · ${formatDuration(durationSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                detail.groupBy { it.set.exerciseOrder }.toSortedMap().forEach { (_, rows) ->
                    Text(
                        "${rows.first().exerciseName}：" +
                            rows.joinToString(", ") { "${it.set.weightKg.displayKg()}kg×${it.set.reps}" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = { showDelete = true }) {
                    Text("删除该次记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除记录") },
            text = { Text("删除「${session.title}」及其全部组记录？不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.deleteSession(session.id)
                        showDelete = false
                        onDeleted()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}
```

- [ ] **Step 3: 构建与验收**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

模拟器验收：日历点有健身的日期 → 卡片显示标题/起止时间/时长；点击展开动作×组明细；删除该次记录后卡片消失、当日圆点消失。

- [ ] **Step 4: 提交**

```bat
git add -A
git commit -m "feat: day detail session cards with expandable sets and delete"
```

---

### Task 12: 导出/导入 UI（SAF）与整体回归

**Files:**
- Create: `app/src/main/java/com/wc/workout/ui/common/UriIo.kt`
- Modify（整文件重写）: `app/src/main/java/com/wc/workout/ui/trend/TrendScreen.kt`

**Interfaces:**
- Consumes: Task 4 `BackupRepository.export()/import()`、Task 7 TrendScreen
- Produces: `writeUriText(context, uri, text)`、`readUriText(context, uri): String`；趋势页底部"数据备份"区

- [ ] **Step 1: UriIo 工具**

`app/src/main/java/com/wc/workout/ui/common/UriIo.kt`:
```kotlin
package com.wc.workout.ui.common

import android.content.Context
import android.net.Uri

fun writeUriText(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("无法写入所选位置")
}

fun readUriText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("无法读取所选文件")
```

- [ ] **Step 2: 整文件重写 TrendScreen（趋势 + 备份区）**

```kotlin
package com.wc.workout.ui.trend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.readUriText
import com.wc.workout.ui.common.viewModelWith
import com.wc.workout.ui.common.writeUriText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrendScreen(container: AppContainer) {
    val vm: TrendViewModel = viewModelWith { TrendViewModel(container.weightRepository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val all by vm.weights.collectAsState()
    val range by vm.range.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                writeUriText(context, uri, container.backupRepository.export())
                snackbar.showSnackbar("备份已导出")
            } catch (e: Exception) {
                snackbar.showSnackbar("导出失败：${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("体重趋势", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendRange.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
                }
            }

            val today = LocalDate.now().toEpochDay()
            val shown = remember(all, range, today) {
                when (val r = range) {
                    TrendRange.ALL -> all
                    else -> all.filter { it.dateEpochDay >= today - (r.days ?: 0) + 1 }
                }
            }
            if (shown.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) { Text("这个范围内还没有体重记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val values = shown.map { it.weightKg }
                val zone = ZoneId.systemDefault()
                val fmt = DateTimeFormatter.ofPattern("MM-dd")
                WeightLineChart(
                    points = shown.map { it.dateEpochDay to it.weightKg },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Instant.ofEpochMilli(shown.first().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                    Text(Instant.ofEpochMilli(shown.last().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最高 ${values.max().kgLabel()}")
                    Text("最低 ${values.min().kgLabel()}")
                    Text("平均 ${values.average().kgLabel()}")
                }
            }

            Text("数据备份", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
                    exportLauncher.launch("workout-backup-$stamp.json")
                }) { Text("导出备份") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("导入备份") }
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("导入备份") },
            text = { Text("将合并数据：同一天的体重会被备份覆盖，训练记录全部追加，同名动作复用。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    scope.launch {
                        try {
                            val s = container.backupRepository.import(readUriText(context, uri))
                            snackbar.showSnackbar("导入完成：${s.weights} 天体重、${s.sessions} 次训练、${s.sets} 组记录")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("导入失败：${e.message}")
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } }
        )
    }
}
```

- [ ] **Step 3: 构建**

Run: `cd /d D:\zcode\data\workout-app && gradlew.bat testDebugUnitTest && gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 整体回归清单（模拟器，逐项核对后勾选）**

1. 体重：训练页录体重 → 日历格子出现 kg → 改系统日期再录另一天 → 趋势图两点成线。
2. 同一天重复录体重 → 覆盖（不出现两条）。
3. 健身全流程：开始（标题兜底/历史标题）→ 添加动作 → 录组（预填/上次参考/编辑/删除组）→ 结束 → 日历显示标题点、详情可展开。
4. 计时抗杀：训练中杀 app → 重开 → 首页卡片计时正确。
5. 动作库：新建/重名/重命名/归档/删除规则（被引用动作无删除项）。
6. 导出：选位置保存 JSON 成功；导出文件用文本查看器可读、结构含 schemaVersion/weights/exercises/sessions/sets。
7. 导入：清空 app 数据（`adb shell pm clear com.wc.workout`）后导入该文件 → 数据完整恢复、统计提示正确。
8. 放弃训练 → 无痕。

- [ ] **Step 5: 提交**

```bat
git add -A
git commit -m "feat: backup export/import via SAF on trend screen"
```

---

## Spec 覆盖对照（自审记录）

| Spec 条目 | 落地任务 |
|---|---|
| §2 决策表（技术栈/增补功能/体重规则） | Task 1、Task 3、Task 4、Task 6 |
| §4 技术选型与权限 | Task 1（版本目录、minSdk 26、manifest 无权限） |
| §5 架构（单模块 MVVM、AppContainer、包结构） | Task 1、Task 3 |
| §6 数据模型（4 表、唯一索引、级联/限制外键） | Task 2 |
| §7 DAO 与核心查询（含上次成绩两步查询） | Task 2、Task 3 |
| §8 页面与导航（4 tab、自绘月历、详情 Sheet、趋势页） | Task 5、Task 6、Task 7、Task 8 |
| §9.1 记录一次健身（标题兜底、startTime 落库、添加动作、预填、上次参考、结束/放弃） | Task 9、Task 10 |
| §9.2 体重录入（覆盖、recordedAt） | Task 3、Task 5、Task 6 |
| §9.3 日历合并渲染 | Task 6 |
| §10 备份格式与导入算法（无 id、index 引用、版本校验、单事务） | Task 4 |
| §10 SAF 导出/导入 | Task 12 |
| §11 错误处理（输入校验、重名、RESTRICT、空状态、时区） | Task 2、3、5、6、8、10、12 各任务内实现 |
| §12 测试策略（DAO/Repository/备份单测，UI 手动验收） | Task 2、3、4 测试 + 各 UI 任务验收步骤 |



