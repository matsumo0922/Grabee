# Compose Multiplatform プロジェクト構成ガイド

このドキュメントは、Kotlin Multiplatform（KMP）と JetBrains Compose Multiplatform（CMP）を用いたモジュール構成とビルドロジックを、新規プロジェクトへ移植するためのテンプレートです。必要なファイルをコピーした後、プレースホルダーを任意の値へ置き換えて利用してください。

## プレースホルダー一覧
- `{{PROJECT_NAME}}` … ルートプロジェクト名（例: `MyApp`）
- `{{APP_PACKAGE}}` … アプリ全体のベースパッケージ（例: `com.example.myapp`）
- `{{ANDROID_APPLICATION_ID}}` … Android の `applicationId`
- `{{ANDROID_APP_NAME}}` … Android リリースビルドのアプリ名
- `{{ANDROID_APP_NAME_DEBUG}}` … Android Debug ビルドのアプリ名
- `{{ANDROID_APP_NAME_BILLING}}` … Android Billing ビルドのアプリ名
- `{{IOS_FRAMEWORK_SUMMARY}}` … iOS ライブラリの概要（Swift Package Manager 用）
- `{{IOS_FRAMEWORK_HOMEPAGE}}` … ライブラリのホームページ URL

> **置換ヒント**: ベースパッケージを `com.example.myapp` にすると、`core`/`feature` 名前空間は `com.example.myapp.core.common` の形式になります。エディタのマルチカーソルや `git grep '{{APP_PACKAGE}}'` を活用すると安全です。

## 全体像
- Android / iOS をターゲットにした Compose Multiplatform プロジェクト。
- 共有コードは `core`（基盤レイヤー）と `feature`（機能レイヤー）に分割。`composeApp` が Android アプリ本体、iOS は別途 Xcode プロジェクト（`iosApp`）を利用。
- 共通設定は `build-logic` ディレクトリにまとめた Convention Plugin 経由で各モジュールへ適用。
- 依存管理は Version Catalog（`gradle/libs.versions.toml`）と bundle を活用して一元化。
- iOS 向けの追加ライブラリは Swift Package Manager で Xcode プロジェクトに直接追加する前提です（CocoaPods は使用しません）。

## 主要ディレクトリ構成
```
.
├── build-logic/          # Convention Plugin 群（includeBuild で読み込み）
├── composeApp/           # Android アプリ (KMP Application)
├── core/                 # 共有ロジック（common/library）
│   ├── billing/
│   ├── common/
│   ├── datasource/
│   ├── model/
│   ├── repository/
│   ├── resource/
│   └── ui/
├── feature/              # 機能モジュール (home, editor, setting, billing)
├── iosApp/               # iOS アプリ (Xcode プロジェクト)
├── config/detekt/        # 静的解析設定
├── docs/                 # ドキュメント類
├── gradle/               # Keystore 等の補助リソース
├── gradle.properties     # 共通 Gradle 設定
├── settings.gradle.kts   # ルート設定 + includeBuild
└── build.gradle.kts      # ルート Plugin 宣言
```

## モジュール一覧
| モジュール | 種別 | 主プラグイン | 主な依存関係 | 役割 |
| --- | --- | --- | --- | --- |
| `composeApp` | KMP Application | `matsumo.primitive.kmp.*`, `matsumo.primitive.android.application` | すべての `core`/`feature`、広告・課金系、BuildKonfig | Android 向けエントリポイント。ビルドタイプや署名設定、BuildKonfig を定義 |
| `core:common` | KMP Library | `matsumo.primitive.kmp.*`, `matsumo.primitive.android.library` | Koin, Firebase, Kotlin stdlib | DI や共通ユーティリティ |
| `core:model` | KMP Library | 同上 | `core:common`, `core:resource`, Ktor | モデル定義と共通ビジネスロジック |
| `core:datasource` | KMP Library | 同上 | DataStore, FileKit | データアクセス層（プラットフォーム連携は SwiftPM/Gradle で個別対応） |
| `core:repository` | KMP Library | 同上 | `core:model/common/datasource/resource`, Ktor | Repository 実装、マルチプラットフォーム通信 |
| `core:resource` | KMP Library | `matsumo.primitive.kmp.compose` | Compose Resources | 共通リソース束ね |
| `core:ui` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群, Compose UI, Adaptive UI | 共通 UI コンポーネント |
| `core:billing` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群, RevenueCat/Purchases | 課金 UI + 購読ロジック |
| `feature:home` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群 | ホーム画面 |
| `feature:editor` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群, ColorPicker, Confetti | 編集画面 |
| `feature:setting` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群, AboutLibraries | 設定画面 |
| `feature:billing` | KMP Library | `matsumo.primitive.kmp.compose` | `core` 群 | 課金関連画面 |

## ビルドロジック

### 1. ルート設定ファイル
以下 3 ファイルをコピーし、プレースホルダーを置換してください。

#### `settings.gradle.kts`
```kotlin
@file:Suppress("UnstableApiUsage")

rootProject.name = "{{PROJECT_NAME}}"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://storage.googleapis.com/r8-releases/raw")
        maven("https://jitpack.io")
    }
}

include(":composeApp")
include(":core:common")
include(":core:ui")
include(":core:datasource")
include(":core:repository")
include(":core:resource")
include(":core:model")
include(":core:billing")
include(":feature:home")
include(":feature:editor")
include(":feature:setting")
include(":feature:billing")
```

#### `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kmpCompose) apply false
    alias(libs.plugins.kmpComplete) apply false
    alias(libs.plugins.kmpSwiftKlib) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.libraries) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.gms) apply false
}
```

#### `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx6g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.configureondemand=false
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=false
android.defaults.buildfeatures.buildconfig=true
android.defaults.buildfeatures.aidl=false
android.defaults.buildfeatures.renderscript=false
android.defaults.buildfeatures.resvalues=true
android.defaults.buildfeatures.shaders=false
```

### 2. Version Catalog
依存は `gradle/libs.versions.toml` にまとめます。必要に応じてバージョンを更新してください。

<details>
<summary><code>gradle/libs.versions.toml</code></summary>

```toml
[versions]
# Application
versionName = "0.0.1"
versionCode = "1"

# SDK
minSdk = "26"
targetSdk = "36"
compileSdk = "36"

# Gradle
gradle = "8.12.0"

# Kotlin
kotlin = "2.2.10"

# KotlinX
kotlinxCoroutines = "1.10.2"
kotlinxDatetime = "0.7.1"
kotlinxSerializationJson = "1.8.1"
kotlinxImmutable = "0.4.0"

# KMP
kmpCompose = "1.9.0-beta03"
kmpComplete = "1.1.0"
kmpSwiftKlib = "0.6.4"
kmpLifecycle = "2.9.2"
kmpNavigation = "2.9.0-beta05"
kmpPurchase = "2.1.0+16.2.0"
adaptive = "1.1.2"

# AndroidX
androidxCore = "1.17.0"
androidxCoreSplash = "1.0.1"
androidxAppCompat = "1.7.1"
androidxActivity = "1.10.1"
androidxFragment = "1.8.9"
androidxDataStore = "1.1.7"
androidxAnnotation = "1.9.1"
androidxPrint = "1.1.0"
compose = "2025.08.00"

# Google
playReview = "2.0.2"
playUpdate = "2.1.0"
playServiceAds = "24.5.0"
playServiceOss = "17.2.2"
ksp = "2.2.0-2.0.2"
gms = "4.4.3"

# Firebase
firebase = "34.1.0"
firebaseCrashlytics = "3.0.6"

# koin
koin = "4.1.0"

# UI
calf = "0.8.0"
zoomable = "2.8.1"
kolor = "3.0.1"
colorPicker = "1.1.2"

# Others
ktor = "3.2.1"
coil3 = "3.2.0"
filekit = "0.10.0-beta04"
detekt = "1.23.8"
libraries = "12.2.4"
composeRichEditor = "1.0.0-rc13"
buildKonfig = "0.17.1"
twitterComposeRule = "0.0.26"

# Debugs
napier = "2.7.1"

[plugins]
android-application = { id = "com.android.application", version.ref = "gradle" }
android-library = { id = "com.android.library", version.ref = "gradle" }
kotlin-compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
gms = { id = "com.google.gms.google-services", version.ref = "gms" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlytics" }
libraries = { id = "com.mikepenz.aboutlibraries.plugin", version.ref = "libraries" }
kmp = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kmpCompose = { id = "org.jetbrains.compose", version.ref = "kmpCompose" }
kmpComplete = { id = "com.louiscad.complete-kotlin", version.ref = "kmpComplete" }
kmpSwiftKlib = { id = "io.github.ttypic.swiftklib", version.ref = "kmpSwiftKlib" }

[libraries]
## Dependencies for build-logic include build
android-r8 = { module = "com.android.tools:r8", version.ref = "r8" }
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "gradle" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
gms-services = { group = "com.google.gms", name = "google-services", version.ref = "gms" }
gms-oss = { group = "com.google.android.gms", name = "oss-licenses-plugin", version = "0.10.6" }
build-konfig-gradlePlugin = { group = "com.codingfeline.buildkonfig", name = "buildkonfig-gradle-plugin", version.ref = "buildKonfig" }
secret-gradlePlugin = { group = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", name = "secrets-gradle-plugin", version = "2.0.1" }
detekt-gradlePlugin = { group = "io.gitlab.arturbosch.detekt", name = "detekt-gradle-plugin", version.ref = "detekt" }
detekt-formatting = { group = "io.gitlab.arturbosch.detekt", name = "detekt-formatting", version.ref = "detekt" }

# Kotlin
kotlin-bom = { module = "org.jetbrains.kotlin:kotlin-bom", version.ref = "kotlin" }
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }

# KotlinX
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "kotlinxImmutable" }

# AndroidX
androidx-core = { module = "androidx.core:core-ktx", version.ref = "androidxCore" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "androidxCoreSplash" }
androidx-annotation = { module = "androidx.annotation:annotation", version.ref = "androidxAnnotation" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidxAppCompat" }
androidx-activity = { module = "androidx.activity:activity-compose", version.ref = "androidxActivity" }
androidx-fragment = { module = "androidx.fragment:fragment-ktx", version.ref = "androidxFragment" }
androidx-print = { module = "androidx.print:print", version.ref = "androidxPrint" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "androidxDataStore" }
androidx-datastore-proto = { module = "androidx.datastore:datastore-core-okio", version.ref = "androidxDataStore" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidxDataStore" }

# Compose BOM and artifacts
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose" }
compose-runtime = { module = "androidx.compose.runtime:runtime" }
compose-runtime-saveable = { module = "androidx.compose.runtime:runtime-saveable" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-animation = { module = "androidx.compose.animation:animation" }
compose-animation-graphics = { module = "androidx.compose.animation:animation-graphics" }
compose-material = { module = "androidx.compose.material:material" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-binding = { module = "androidx.compose.ui:ui-viewbinding" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources" }
compose-components-uiToolingPreview = { module = "org.jetbrains.compose.components:components-ui-tooling-preview" }

# Adaptive UI
adaptive = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "adaptive" }
adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "adaptive" }
adaptive-navigation = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "adaptive" }

# Google Play Services
play-review = { module = "com.google.android.play:review-ktx", version.ref = "playReview" }
play-update = { module = "com.google.android.play:app-update-ktx", version.ref = "playUpdate" }
play-service-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "playServiceAds" }
play-service-oss = { module = "com.google.android.gms:play-services-oss-licenses", version.ref = "playServiceOss" }

# Firebase
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase" }
firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }

# RevenueCat
purchases-core = { module = "com.revenuecat.purchases:purchases", version.ref = "kmpPurchase" }
purchases-result = { module = "com.revenuecat.purchases:purchases-result", version.ref = "kmpPurchase" }

# FileKit
filekit-core = { module = "io.github.vinceglb:filekit-core", version.ref = "filekit" }
filekit-dialogs = { module = "io.github.vinceglb:filekit-dialogs-compose", version.ref = "filekit" }
filekit-coil = { module = "io.github.vinceglb:filekit-coil", version.ref = "filekit" }

# Coil
coil3-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil3" }
coil3-network = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil3" }

# Others
ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktot-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

colorpicker = { module = "com.github.skydoves:colorpicker-compose", version.ref = "colorPicker" }
kolor = { module = "com.materialkolor:material-kolor", version.ref = "kolor" }

# Debug
napier = { module = "io.github.aakira:napier", version.ref = "napier" }
twitter-compose-rule = { module = "com.twitter.compose.rules:detekt", version.ref = "twitterComposeRule" }

[bundles]
infra = [
    "kotlin-stdlib",
    "kotlin-reflect",
    "kotlinx-datetime",
    "kotlinx-serialization-json",
    "kotlinx-collections-immutable",
    "napier",
]

ui-android = [
    "androidx-core",
    "androidx-annotation",
    "androidx-appcompat",
    "androidx-activity",
    "androidx-activity-compose",
    "androidx-fragment",
    "compose-runtime",
    "compose-ui",
    "compose-ui-binding",
]

ui-common = [
    "kmp-lifecycle-runtime-compose",
    "kmp-lifecycle-viewmodel-compose",
    "kmp-navigation-compose",
    "coil3-compose",
    "coil3-network",
    "kolor",
]

purchase = [
    "purchases-core",
    "purchases-result",
]

ktor = [
    "ktor-core",
    "ktor-cio",
    "ktor-content-negotiation",
    "ktor-serialization-json",
    "ktot-logging",
]

koin = [
    "koin-core",
    "koin-compose",
    "koin-compose-viewmodel",
]

calf = [
    "calf-ui",
    "calf-permission",
    "calf-filepicker",
]

firebase = [
    "firebase-analytics",
    "firebase-crashlytics",
]

filekit = [
    "filekit-core",
    "filekit-dialogs",
    "filekit-coil",
]
```

</details>

### 3. `build-logic`（Convention Plugins）
`build-logic` ディレクトリは丸ごとコピーしてください。`{{APP_PACKAGE}}` は実際のパッケージへ置換します。CocoaPods 連携用プラグインは削除済みです。

#### `build-logic/settings.gradle.kts`
```kotlin
@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
```

#### `build-logic/build.gradle.kts`
```kotlin
plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    implementation(libs.android.r8)
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.secret.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.build.konfig.gradlePlugin)
    implementation(libs.gms.services)
    implementation(libs.gms.oss)
}

gradlePlugin {
    plugins {
        register("AndroidApplicationPlugin") {
            id = "matsumo.primitive.android.application"
            implementationClass = "primitive.AndroidApplicationPlugin"
        }
        register("AndroidLibraryPlugin") {
            id = "matsumo.primitive.android.library"
            implementationClass = "primitive.AndroidLibraryPlugin"
        }
        register("KmpPlugin") {
            id = "matsumo.primitive.kmp.common"
            implementationClass = "primitive.KmpCommonPlugin"
        }
        register("KmpAndroidPlugin") {
            id = "matsumo.primitive.kmp.android"
            implementationClass = "primitive.KmpAndroidPlugin"
        }
        register("KmpAndroidCompose") {
            id = "matsumo.primitive.kmp.compose"
            implementationClass = "primitive.KmpComposePlugin"
        }
        register("KmpIosPlugin") {
            id = "matsumo.primitive.kmp.ios"
            implementationClass = "primitive.KmpIosPlugin"
        }
        register("DetektPlugin") {
            id = "matsumo.primitive.detekt"
            implementationClass = "primitive.DetektPlugin"
        }
    }
}
```

#### Utility DSL (`build-logic/src/main/kotlin/{{APP_PACKAGE_PATH}}/`)
パッケージパス `{{APP_PACKAGE_PATH}}`（例: `com/example/myapp`）に以下 4 ファイルを配置します。

##### `GradleDsl.kt`
```kotlin
package {{APP_PACKAGE}}

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.configure

fun DependencyHandlerScope.implementation(artifact: Dependency) {
    add("implementation", artifact)
}

fun DependencyHandlerScope.implementation(artifact: MinimalExternalModuleDependency) {
    add("implementation", artifact)
}

fun DependencyHandlerScope.implementation(artifact: ExternalModuleDependencyBundle) {
    add("implementation", artifact)
}

fun DependencyHandlerScope.implementation(provider: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", provider)
}

fun DependencyHandlerScope.debugImplementation(artifact: MinimalExternalModuleDependency) {
    add("debugImplementation", artifact)
}

fun DependencyHandlerScope.androidTestImplementation(artifact: Dependency) {
    add("androidTestImplementation", artifact)
}

fun DependencyHandlerScope.androidTestImplementation(artifact: MinimalExternalModuleDependency) {
    add("androidTestImplementation", artifact)
}

fun DependencyHandlerScope.testImplementation(artifact: MinimalExternalModuleDependency) {
    add("testImplementation", artifact)
}

fun DependencyHandlerScope.implementationPlatform(artifact: MinimalExternalModuleDependency) {
    add("implementation", platform(artifact))
}

fun DependencyHandlerScope.lintChecks(artifact: MinimalExternalModuleDependency) {
    add("lintChecks", artifact)
}

private fun DependencyHandlerScope.api(artifact: MinimalExternalModuleDependency) {
    add("api", artifact)
}

fun Project.java(action: JavaPluginExtension.() -> Unit) {
    extensions.configure(action)
}
```

##### `AndroidGradleDsl.kt`
```kotlin
package {{APP_PACKAGE}}

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

fun Project.androidApplication(action: BaseAppModuleExtension.() -> Unit) {
    extensions.configure(action)
}

fun Project.androidLibrary(action: LibraryExtension.() -> Unit) {
    extensions.configure(action)
}

fun Project.android(action: TestedExtension.() -> Unit) {
    extensions.configure(action)
}

fun Project.setupAndroid() {
    android {
        defaultConfig {
            targetSdk = libs.version("targetSdk").toInt()
            minSdk = libs.version("minSdk").toInt()

            javaCompileOptions {
                annotationProcessorOptions {
                    arguments += mapOf(
                        "room.schemaLocation" to "$projectDir/schemas",
                        "room.incremental" to "true"
                    )
                }
            }

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        splits {
            abi {
                isEnable = true
                isUniversalApk = true

                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }

        dependencies {
            add("coreLibraryDesugaring", libs.library("desugar"))
        }
    }
}
```

##### `VersionCatalogDsl.kt`
```kotlin
package {{APP_PACKAGE}}

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugin.use.PluginDependency

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(name: String): String {
    return findVersion(name).get().requiredVersion
}

internal fun VersionCatalog.library(name: String): MinimalExternalModuleDependency {
    return findLibrary(name).get().get()
}

internal fun VersionCatalog.plugin(name: String): PluginDependency {
    return findPlugin(name).get().get()
}

internal fun VersionCatalog.bundle(name: String): Provider<ExternalModuleDependencyBundle> {
    return findBundle(name).get()
}
```

##### `Detekt.kt`
```kotlin
package {{APP_PACKAGE}}

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

@Suppress("UNCHECKED_CAST")
internal fun Project.configureDetekt() {
    extensions.getByType<DetektExtension>().apply {
        toolVersion = libs.version("detekt")
        parallel = true
        config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
        baseline = file("${project.rootDir}/config/detekt/baseline.xml")
        buildUponDefaultConfig = true
        ignoreFailures = false
        autoCorrect = false
    }

    val reportMerge = if (!rootProject.tasks.names.contains("reportMerge")) {
        rootProject.tasks.register("reportMerge", ReportMergeTask::class) {
            output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.xml"))
        }
    } else {
        rootProject.tasks.named("reportMerge") as TaskProvider<ReportMergeTask>
    }

    plugins.withType<io.gitlab.arturbosch.detekt.DetektPlugin> {
        tasks.withType<io.gitlab.arturbosch.detekt.Detekt> detekt@{
            finalizedBy(reportMerge)

            source = project.files("./").asFileTree

            include("**/*.kt")
            include("**/*.kts")
            exclude("**/resources/**")
            exclude("**/build/**")

            reportMerge.configure {
                input.from(this@detekt.xmlReportFile)
            }
        }
    }
}
```

#### Convention Plugin 実装 (`build-logic/src/main/kotlin/primitive/`)
以下のプラグインを配置します。

##### `AndroidApplicationPlugin.kt`
```kotlin
package primitive

import {{APP_PACKAGE}}.androidApplication
import {{APP_PACKAGE}}.libs
import {{APP_PACKAGE}}.setupAndroid
import {{APP_PACKAGE}}.version
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("kotlin-parcelize")
                apply("kotlinx-serialization")
                apply("project-report")
                apply("com.google.gms.google-services")
                apply("com.google.firebase.crashlytics")
                apply("com.google.devtools.ksp")
                apply("com.mikepenz.aboutlibraries.plugin")
                apply("com.codingfeline.buildkonfig")
            }

            androidApplication {
                setupAndroid()

                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                buildFeatures.viewBinding = true

                defaultConfig {
                    applicationId = "{{ANDROID_APPLICATION_ID}}"

                    versionName = libs.version("versionName")
                    versionCode = libs.version("versionCode").toInt()
                }

                packaging {
                    resources.excludes.addAll(
                        listOf(
                            "LICENSE",
                            "LICENSE.txt",
                            "NOTICE",
                            "asm-license.txt",
                            "cglib-license.txt",
                            "mozilla/public-suffix-list.txt",
                        )
                    )
                }
            }
        }
    }
}
```

##### `AndroidLibraryPlugin.kt`
```kotlin
package primitive

import com.android.build.gradle.LibraryExtension
import {{APP_PACKAGE}}.androidLibrary
import {{APP_PACKAGE}}.libs
import {{APP_PACKAGE}}.setupAndroid
import {{APP_PACKAGE}}.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("kotlin-parcelize")
                apply("kotlinx-serialization")
                apply("project-report")
                apply("com.google.devtools.ksp")
            }

            androidLibrary {
                setupAndroid()
            }

            extensions.configure<LibraryExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                buildFeatures.viewBinding = true
            }
        }
    }
}
```

##### `KmpCommonPlugin.kt`
```kotlin
package primitive

import {{APP_PACKAGE}}.library
import {{APP_PACKAGE}}.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpCommonPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
            }

            kotlin {
                sourceSets.all {
                    languageSettings.enableLanguageFeature("ExplicitBackingFields")
                }

                sourceSets.commonMain.dependencies {
                    val kotlinBom = libs.library("kotlin-bom")
                    implementation(project.dependencies.platform(kotlinBom))
                }
            }
        }
    }
}

fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
    extensions.configure(action)
}
```

##### `KmpAndroidPlugin.kt`
```kotlin
package primitive

import {{APP_PACKAGE}}.android
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("unused")
class KmpAndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            kotlin {
                androidTarget {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }

            android {
                sourceSets {
                    getByName("main") {
                        manifest.srcFile("src/androidMain/AndroidManifest.xml")
                        res.srcDirs("src/androidMain/res")
                    }
                }
            }
        }
    }
}
```

##### `KmpComposePlugin.kt`
```kotlin
package primitive

import {{APP_PACKAGE}}.android
import {{APP_PACKAGE}}.androidTestImplementation
import {{APP_PACKAGE}}.debugImplementation
import {{APP_PACKAGE}}.implementation
import {{APP_PACKAGE}}.library
import {{APP_PACKAGE}}.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KmpComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.library("compose-bom")

                implementation(project.dependencies.platform(bom))
                implementation(libs.library("compose-ui-tooling-preview"))
                debugImplementation(libs.library("compose-ui-tooling"))
                androidTestImplementation(project.dependencies.platform(bom))
            }
        }
    }
}
```

##### `KmpIosPlugin.kt`
```kotlin
package primitive

import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpIosPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            kotlin {
                applyDefaultHierarchyTemplate()

                iosX64()
                iosArm64()
                iosSimulatorArm64()

                sourceSets.named { it.lowercase().startsWith("ios") }.configureEach {
                    languageSettings {
                        optIn("kotlinx.cinterop.ExperimentalForeignApi")
                    }
                }
            }
        }
    }
}
```

##### `DetektPlugin.kt`
```kotlin
package primitive

import {{APP_PACKAGE}}.configureDetekt
import {{APP_PACKAGE}}.library
import {{APP_PACKAGE}}.libs
import {{APP_PACKAGE}}.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class DetektPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("detekt").pluginId)

            configureDetekt()

            dependencies {
                "detektPlugins"(libs.library("detekt-formatting"))
                "detektPlugins"(libs.library("twitter-compose-rule"))
            }
        }
    }
}
```

### 4. 静的解析設定
`config/detekt/detekt.yml` をコピーします。`baseline.xml` は必要時に生成してください。

```yaml
processors:
  active: true
  exclude:
    - 'FunctionCountingProcessor'
    - 'PropertyCountingProcessor'
console-reports:
  active: true
config:
  validation: true
  warningsAsErrors: false
  excludedPaths:
    - '.*generated.*'
    - '.*/build/.*'
project:
  processing:
    active: true
    failFast: false
    autoCorrect: false
  build:
    maxIssues: 0
style:
  active: true
  MagicNumber:
    active: false
  WildcardImport:
    active: false
  MaxLineLength:
    active: true
    maxLineLength: 140
naming:
  active: true
performance:
  active: true
github:
  active: false
coroutines:
  active: true
complexity:
  active: true
  LongMethod:
    active: true
    threshold: 80
  TooManyFunctions:
    active: true
    thresholdInFiles: 30
    thresholdInClasses: 30
    thresholdInInterfaces: 30
    thresholdInObjects: 30
    thresholdInEnums: 30
    ignoreDeprecated: true
    ignorePrivate: true
formatting:
  active: true
  KtLint:
    enabled: true
  Indentation:
    active: true
    indentSize: 4
    continuationIndentSize: 4
  ParameterListWrapping:
    active: true
  MaximumLineLength:
    active: true
    maxLineLength: 140
    ignoreBackTickedIdentifier: true
  NoUnusedImports:
    active: true
```

## モジュール別 `build.gradle.kts`
各モジュールの namespace は `{{APP_PACKAGE}}` を基準に置換します。MLKit や CocoaPods に依存した記述は削除済みです。

### `composeApp/build.gradle.kts`
```kotlin
@file:Suppress("UnusedPrivateProperty")

import com.android.build.api.variant.ResValue
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.application")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

val localProperties = Properties().apply {
    project.rootDir.resolve("local.properties").also {
        if (it.exists()) load(it.inputStream())
    }
}

val admobTestAppId = "ca-app-pub-0000000000000000~0000000000"
val bannerAdTestId = "ca-app-pub-3940256099942544/6300978111"
val nativeAdTestId = "ca-app-pub-3940256099942544/2247696110"
val rewardAdTestId = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "{{APP_PACKAGE}}"

    signingConfigs {
        getByName("debug") {
            storeFile = file("${project.rootDir}/gradle/keystore/debug.keystore")
        }
        create("release") {
            storeFile = file("${project.rootDir}/gradle/keystore/release.keystore")
            storePassword = localProperties.getProperty("storePassword") ?: System.getenv("RELEASE_STORE_PASSWORD")
            keyPassword = localProperties.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
            keyAlias = localProperties.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
        }
        create("billing") {
            storeFile = file("${project.rootDir}/gradle/keystore/release.keystore")
            storePassword = localProperties.getProperty("storePassword") ?: System.getenv("RELEASE_STORE_PASSWORD")
            keyPassword = localProperties.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
            keyAlias = localProperties.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            versionNameSuffix = ".D"
            applicationIdSuffix = ".debug"
        }
        create("billing") {
            signingConfig = signingConfigs.getByName("billing")
            isDebuggable = true
            matchingFallbacks.add("debug")
        }
    }

    androidComponents {
        onVariants {
            val appName = when (it.buildType) {
                "debug" -> "{{ANDROID_APP_NAME_DEBUG}}"
                "billing" -> "{{ANDROID_APP_NAME_BILLING}}"
                else -> null
            }

            it.manifestPlaceholders.apply {
                put("ADMOB_ANDROID_APP_ID", localProperties.getProperty("ADMOB_ANDROID_APP_ID") ?: admobTestAppId)
                put("ADMOB_IOS_APP_ID", localProperties.getProperty("ADMOB_IOS_APP_ID") ?: admobTestAppId)
            }

            if (appName != null) {
                it.resValues.apply {
                    put(it.makeResValueKey("string", "app_name"), ResValue(appName, null))
                }
            }

            if (it.buildType == "release") {
                it.packaging.resources.excludes.add("META-INF/**")
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:datasource"))
            implementation(project(":core:repository"))
            implementation(project(":core:ui"))
            implementation(project(":core:resource"))
            implementation(project(":core:billing"))

            implementation(project(":feature:home"))
            implementation(project(":feature:editor"))
            implementation(project(":feature:setting"))
            implementation(project(":feature:billing"))
        }

        androidMain.dependencies {
            implementation(libs.bundles.mediation)

            implementation(libs.androidx.core.splashscreen)
            implementation(libs.play.review)
            implementation(libs.play.update)
            implementation(libs.google.material)
            implementation(libs.koin.androidx.startup)
        }
    }
}

buildkonfig {
    packageName = "{{APP_PACKAGE}}"

    defaultConfigs {
        fun setField(name: String, defaultValue: String = "") {
            val envValue = System.getenv(name)
            val propertyValue = localProperties.getProperty(name)

            buildConfigField(FieldSpec.Type.STRING, name, propertyValue ?: envValue ?: defaultValue)
        }

        setField("VERSION_NAME", libs.versions.versionName.get())
        setField("VERSION_CODE", libs.versions.versionCode.get())

        setField("DEVELOPER_PIN", "1234")
        setField("PURCHASE_ANDROID_API_KEY")
        setField("PURCHASE_IOS_API_KEY")

        setField("ADMOB_ANDROID_APP_ID", admobTestAppId)
        setField("ADMOB_ANDROID_BANNER_AD_UNIT_ID", admobTestAppId)
        setField("ADMOB_ANDROID_INTERSTITIAL_AD_UNIT_ID", bannerAdTestId)

        setField("ADMOB_IOS_APP_ID", admobTestAppId)
        setField("ADMOB_IOS_BANNER_AD_UNIT_ID", bannerAdTestId)
        setField("ADMOB_IOS_INTERSTITIAL_AD_UNIT_ID", bannerAdTestId)

        setField("APPLOVIN_SDK_KEY")
    }
}
```

### `core/common/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project.dependencies.platform(libs.koin.bom))

            api(libs.bundles.infra)
            api(libs.bundles.koin)
        }

        androidMain.dependencies {
            api(project.dependencies.platform(libs.firebase.bom))

            api(libs.bundles.firebase)
            api(libs.koin.android)
        }
    }
}
```

### `core/model/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:resource"))

            implementation(libs.ktor.core)
        }
    }
}
```

### `core/datasource/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.datasource"
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.proto)
        }

        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:resource"))

            api(libs.bundles.filekit)
            api(libs.androidx.datastore.preferences)
        }
    }
}
```

### `core/repository/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.repository"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:datasource"))
            implementation(project(":core:resource"))

            implementation(libs.bundles.ktor)
        }

        androidMain.dependencies {
            api(libs.ktor.okhttp)
        }

        iosMain.dependencies {
            api(libs.ktor.darwin)
        }
    }
}
```

### `core/resource/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.resource"
}

compose.resources {
    publicResClass = true
    packageOfResClass = "{{APP_PACKAGE}}.core.resource"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.components.resources)
        }
    }
}
```

### `core/ui/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:repository"))
            implementation(project(":core:datasource"))
            implementation(project(":core:resource"))

            api(libs.bundles.ui.common)
            api(libs.bundles.calf)

            api(compose.runtime)
            api(compose.runtimeSaveable)
            api(compose.foundation)
            api(compose.animation)
            api(compose.animationGraphics)
            api(compose.material)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
            api(compose.components.uiToolingPreview)

            api(libs.adaptive)
            api(libs.adaptive.layout)
            api(libs.adaptive.navigation)

            api(libs.rich.editor)
        }

        androidMain.dependencies {
            api(libs.bundles.ui.android)
            implementation(libs.androidx.print)
            implementation(libs.play.service.ads)
        }
    }
}
```

### `core/billing/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.core.billing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:resource"))
            implementation(project(":core:ui"))

            implementation(libs.bundles.purchase)
        }
    }
}
```

### `feature/home/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.feature.home"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:repository"))
            implementation(project(":core:datasource"))
            implementation(project(":core:ui"))
            implementation(project(":core:resource"))
            implementation(project(":core:billing"))
        }
    }
}
```

### `feature/editor/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.feature.editor"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:repository"))
            implementation(project(":core:datasource"))
            implementation(project(":core:ui"))
            implementation(project(":core:resource"))
            implementation(project(":core:billing"))

            implementation(libs.colorpicker)
            implementation(libs.confetti)
        }
    }
}
```

### `feature/setting/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.feature.setting"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:repository"))
            implementation(project(":core:datasource"))
            implementation(project(":core:ui"))
            implementation(project(":core:resource"))
            implementation(project(":core:billing"))

            implementation(libs.libraries.ui)
        }
    }
}
```

### `feature/billing/build.gradle.kts`
```kotlin
plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "{{APP_PACKAGE}}.feature.billing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:repository"))
            implementation(project(":core:datasource"))
            implementation(project(":core:ui"))
            implementation(project(":core:resource"))
            implementation(project(":core:billing"))
        }
    }
}
```

## iOS 側の依存管理について
- iOS の追加ライブラリは Xcode プロジェクトで Swift Package Manager を使って管理してください。共有モジュールの生成結果（`ComposeApp` フレームワークなど）に加え、必要なネイティブライブラリを `iosApp` プロジェクトから直接参照します。
- Swift Package で提供されない依存がある場合は、Kotlin Native の cinterop や別途ラッパーを検討してください。CocoaPods 用の Gradle 設定はこのテンプレートには含めていません。

## 再現手順チェックリスト
1. ルート直下に `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` を配置し、`{{PROJECT_NAME}}` 等を置換。
2. `gradle/libs.versions.toml` を作成して上記内容をコピー。MLKit や CocoaPods が不要な構成になっています。
3. `build-logic` をコピーし、`{{APP_PACKAGE}}` の置換を実施。`settings.gradle.kts` の `includeBuild("build-logic")` を確認。
4. `config/detekt/detekt.yml` を配置。必要なら `./gradlew detektBaseline` で baseline を生成。
5. `composeApp`、`core/*`、`feature/*` の各モジュールを作成し、本書掲載の `build.gradle.kts` を配置。namespace・package を置換。
6. Android 用 keystore や `local.properties` の値（広告 ID、課金鍵など）を設定。CI では環境変数で上書き可能。
7. `chmod +x gradlew` 後、`./gradlew assembleDebug` などでビルド確認。
8. iOS 側は `iosApp` の Swift Package 設定で必要なネイティブ依存を追加し、`./gradlew shared:packForXcode` 相当のタスク実行後に Xcode でビルド。

## 運用メモ
- BuildKonfig のフィールドは環境変数や `local.properties` で上書きできます。秘匿情報はコミットしないよう注意してください。
- Firebase や広告ネットワークを利用する場合は、`google-services.json` や `GoogleService-Info.plist` などの設定ファイルを適切に配置し、ビルドタイプに応じて差し替えてください。
- iOS 用 Swift Package の参照 URL やバージョンはチームで管理し、`iosApp` プロジェクトの Package Dependencies で更新してください。
