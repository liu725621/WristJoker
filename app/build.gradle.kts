plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wearos.wristjoker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wearos.wristjoker"
        // 三星 Galaxy Watch7 出厂为 Wear OS 5（API 34）。
        // compileSdk 用 36 是为了满足 Wear Compose 1.5.0 的 minCompileSdk=35，
        // targetSdk 对齐真机 34，避免在 Wear OS 5 上走未验证的新行为。
        minSdk = 30
        targetSdk = 34
        versionCode = 14
        versionName = "1.13-watch"
    }

    buildTypes {
        release {
            // debug 签名，仅用于侧载验证；正式分发需替换为自有签名。
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// 依赖栈与 WearHello 一致：2025 年中的稳定组合。
// 不要升级到 2026.x：Wear Compose 1.7.1 / Compose 1.12.x / core-ktx 1.19.x
// 的 AAR metadata 均要求 compileSdk 37 + AGP 9.1.0+。
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")

    // AOD 常亮（ambient mode）：AmbientLifecycleObserver 在 wear 主库里。
    // 不用 ambient 独立依赖 —— 1.3.0 起 ambient 已并入 androidx.wear:wear。
    implementation("androidx.wear:wear:1.4.0")

    // 手表「卡片」（三星叫卡片，Wear OS 叫 Tile）。不引 tiles-material：
    // 卡片只有一块 192×192 的圆屏，手写 protolayout 反而比套组件更好控比例。
    implementation("androidx.wear.tiles:tiles:1.5.0")
    // TileService 的回调要返回 ListenableFuture。tiles 只把 concurrent-futures
    // 当运行时依赖带进来，编译期拿不到 ResolvableFuture，所以显式声明一次。
    implementation("androidx.concurrent:concurrent-futures:1.1.0")

    // 表盘小部件（Complication）：把手游的"当前底注 / 分数"做成表盘上的一格。
    // 和 Tile 是两种不同的入口 —— Tile 要右滑，Complication 就在表盘上。
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")

    // 游戏核心逻辑是纯 Kotlin，单元测试只依赖 JUnit。
    testImplementation("junit:junit:4.13.2")
}
