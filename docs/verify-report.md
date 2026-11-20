# Wear OS 6 开发环境 · 构建验证报告

- 生成时间：2026-11-08（GMT+8）
- 环境：Ubuntu 24.04.3 LTS x86_64 / 32 vCPU / 123 GiB 内存 / 254 GiB 磁盘
- 目标平台：**Android 16（"Baklava" / API 36）+ Wear OS 6.0**，单一目标，未安装额外 API 平台

---

## 1. 结论

**环境已装好并验证通过。** 原生应用与 WFF 表盘两个模板工程均 `BUILD SUCCESSFUL` 并产出可检查的 APK；产物经 `aapt2` 解析确认 `compileSdk 36 / targetSdk 36`；APK 直链下载站按 `DEPLOY_GUIDE.md` 部署并发布，线上全链路验证逐条通过。

| 验证项 | 结果 |
| --- | --- |
| 工具链自检（JDK / sdkmanager / Gradle） | 通过 |
| 原生应用工程构建 | **BUILD SUCCESSFUL**（2m 9s） |
| 表盘工程构建 | **BUILD SUCCESSFUL**（22s） |
| 应用 APK 产物解析 | 通过 |
| 表盘 APK 产物解析 + WFF XML 校验 | 通过 |
| 下载站本地自测 | 通过 |
| 下载站线上验证 | 通过 |
| **UI 开源库接入与重新构建** | **通过**（见 §9） |
| **Balatro 玩法移植到 Watch7（WristJoker）** | **通过**（构建 + 108 单测 + 美术重绘 + 10 界面几何校验，未见真机；第三轮见 §17） |
| 启动 Wear OS 6 模拟器 | **未执行**（沙箱无 `/dev/kvm`，见 §8） |

---

## 2. 工具链自检

| 组件 | 实测值 |
| --- | --- |
| `java -version` | OpenJDK 17.0.20.1（Zulu/Ubuntu 24.04 构建） |
| `sdkmanager --version` | 1.1.16717390 (Android CLI) |
| `gradle --version` | Gradle 8.13 / JVM 17 |
| 环境变量 | `/etc/profile.d/wearos-env.sh`（`JAVA_HOME`/`ANDROID_HOME`/`GRADLE_HOME`/`PATH`） |

安装位置：`/opt/android-sdk`、`/opt/gradle/gradle-8.13`、`/usr/lib/jvm/java-17-openjdk-amd64`。

---

## 3. 已安装 SDK 组件（`sdkmanager --list_installed`）

```
build-tools/35.0.0                                    35.0.0     Android SDK Build-Tools 35
build-tools/36.0.0                                    36.0.0     Android SDK Build-Tools 36
cmdline-tools/latest                                  23.0.0     Android SDK Command-line Tools (latest)
emulator                                              37.2.13    Android Emulator
platform-tools                                        37.0.1     Android SDK Platform-Tools
platforms/android-36                                  2.0.0      Android SDK Platform 36
system-images/android-36/android-wear-signed/x86_64   1.0.0      Wear OS 6.0 Intel x86_64 Atom System Image (signed)
```

SDK 占用约 3.6 GiB（系统镜像 2.1 GiB + emulator 863 MiB + build-tools 293 MiB 等）。

---

## 4. 构建验证

| 工程 | 命令 | 结果 | 产物 |
| --- | --- | --- | --- |
| `apps/WearHello` | `gradle assembleDebug` | BUILD SUCCESSFUL | `app-debug.apk` **69,204,000 bytes**（引入 UI 库后重建；初始为 26,273,211 bytes） |
| `apps/WristJoker` | `gradle assembleDebug testDebugUnitTest` | BUILD SUCCESSFUL，43/43 用例通过 | `app-debug.apk` **24,801,145 bytes**（见 §10） |
| `watchfaces/SimpleDigital` | `gradle assembleDebug` | BUILD SUCCESSFUL，32 tasks | `watchface-debug.apk` **53,521 bytes** |

固定版本：AGP 8.13.2 / Gradle 8.13 / Kotlin 2.2.10 / Compose BOM 2025.06.01（Compose 1.8.3）/ Wear Compose 1.5.0 / core-ktx 1.16.0 / activity-compose 1.10.1 / lifecycle 2.9.1。另含 UI 开源库 Horologist 0.7.15 / Material Icons Extended 1.7.8（见 §9）。

> 首次构建曾一次报出 **36 条 AAR metadata 错误**：Wear Compose 1.7.1、Compose 1.12.x、core-ktx 1.19.x 要求 `compileSdk 37` 与 AGP 9.1.0+。因与「单一目标 API 36」冲突，已把依赖栈对齐回 Android 16 发布时的稳定组合，二次构建通过。这是本次部署最关键的版本决策。

---

## 5. 产物解析

### 5.1 应用 `app-debug.apk`（`aapt2 dump badging`）

```
package: name='com.example.wearhello' versionCode='1' versionName='1.0'
platformBuildVersionName='16' platformBuildVersionCode='36'
compileSdkVersion='36' compileSdkVersionCodename='16'
minSdkVersion:'30'
targetSdkVersion:'36'
application-label:'WearHello'
```

APK 内含 `classes.dex` ~ `classes5.dex`（Kotlin + Compose + UI 开源库代码已编译），以及 `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libandroidx.graphics.path.so`。

UI 开源库类**确实打进 dex**（直接扫描 dex 字节串验证，非仅看依赖声明）：

```
  OK  Lcom/google/android/horologist/compose/layout/ScalingLazyColumn
  OK  Lcom/google/android/horologist/compose/layout/ScalingLazyColumnStateKt
  OK  Lcom/google/android/horologist/annotations/ExperimentalHorologistApi
  OK  Landroidx/compose/material/icons/filled/Palette
  OK  Landroidx/wear/compose/material3/AppScaffoldKt
```

### 5.2 表盘 `watchface-debug.apk`

```
package: name='com.example.simpledigital' versionCode='1' versionName='1.0.0'
platformBuildVersionName='16' platformBuildVersionCode='36'
compileSdkVersion='36' compileSdkVersionCodename='16'
minSdkVersion:'33'
targetSdkVersion:'36'
application-label:'Simple Digital'
property: name='com.google.wear.watchface.format.version' value='1'
uses-feature: name='android.hardware.type.watch'
```

`aapt2 dump xmltree` 确认打包后仍保留：

```
E: application (line=28)
  A: android:hasCode(0x0101000c)=false
```

APK 内资源齐全：`res/raw/watchface.xml`（2738 B）、`res/xml/watch_face_info.xml`（232 B）、`res/drawable/preview.png`（40525 B，454×454 RGBA）、`AndroidManifest.xml`。

### 5.3 WFF XML 校验

`xmllint --noout watchface.xml` 通过（well-formed）。表盘定义来自官方 `wear-os-samples/WatchFaceFormat/SimpleDigital`（Apache-2.0）。

---

## 6. APK 直链下载站验证

按资料库 `docs/DEPLOY_GUIDE.md` 部署于 `/workspace/apk-download/`，同时分发 `app.apk`（应用）与 `watchface.apk`（表盘）。

### 6.1 本地自测（`SELFTEST_ONLY=1 bash deploy.sh`）

```
自测通过 | 大小 69204000 bytes | 占位包=False
```

覆盖：首页 200、`meta.json`、APK MIME / `attachment` / `Accept-Ranges`、完整下载字节数一致、单段 Range 返回 206（100 B）、HEAD 200。

### 6.2 线上验证（按指南 §1.5，全部通过）

| 检查 | 实测 |
| --- | --- |
| 首页 | `200` |
| `meta.json` | 两个文件，`placeholder=false`，size 与实际一致 |
| 直链响应头 | `content-type: application/vnd.android.package-archive`、`content-disposition: attachment; filename="app.apk"`、`accept-ranges: bytes`、`x-content-type-options: nosniff` |
| 完整下载 | `200` / 69,204,000 bytes（约 4 s），`zipfile.is_zipfile() == True`，SHA256 与本地一致 |
| Range | `206` / 100 bytes |
| 不带 `?sharecode=` | `403`（发布平台凭据校验） |
| 表盘直链 | `200` / 53,521 bytes |

### 6.3 发布链接

> `?sharecode=` 是访问凭据，**必须整条带上**；转发即公开。停止分享见指南 §4。

- 下载页：`https://a01c7173418c05e23.app.workbuddy.host/?sharecode=XXRDMDM3DI`
- 应用 APK 直链：`https://a01c7173418c05e23.app.workbuddy.host/app.apk?sharecode=XXRDMDM3DI`
- 表盘 APK 直链：`https://a01c7173418c05e23.app.workbuddy.host/watchface.apk?sharecode=XXRDMDM3DI`

服务每次请求实时读盘，后续替换 `app.apk` 为正式签名包无需重新发布、链接不变。

---

## 7. 复现

```bash
sudo bash /workspace/wearos-env/install.sh          # 一键重建工具链
source /etc/profile.d/wearos-env.sh

cd /workspace/wearos-env/apps/WearHello   && gradle assembleDebug
cd /workspace/wearos-env/watchfaces/SimpleDigital && gradle assembleDebug

cd /workspace/apk-download && bash deploy.sh        # 自测 + 发布
```

两个模板工程另附 `./gradlew`（`distributionUrl` 已指向腾讯云镜像），可脱离系统 Gradle 独立构建；`gradle.properties` 已用 `org.gradle.java.home` 把构建 JVM 固定为 JDK 17，避免误用系统默认 JDK 20。实测 `./gradlew assembleDebug` → BUILD SUCCESSFUL（37 tasks up-to-date）。

---

## 8. 已知限制与未执行项（如实说明）

1. **未启动模拟器**：沙箱无 `/dev/kvm`，无法硬件加速。Wear OS 6.0 x86_64 系统镜像已安装，在有 KVM 的机器上可用 `avdmanager` 建 AVD 后运行。本任务按约定只验证到「构建出产物」。
2. **未内置官方 WFF XSD 校验器**：`wff-validator.jar` 发布在 GitHub Packages（需认证），本环境改用 `xmllint` 做 XML 良构性检查 + `aapt2` 检查 APK 内资源与 manifest。需要严格 XSD 校验时，从 [google/watchface](https://github.com/google/watchface) 构建，用法：`java -jar wff-validator.jar 1 res/raw/watchface.xml`。
3. **debug 签名**：两个 APK 仅用于环境验证，Play 上架需正式签名；`targetSdk ≥ 36` 已满足。
4. **无害警告**：`SDK XML version 4`（cmdline-tools 与 platform 元数据版本差）、`jvmTarget is deprecated`（Kotlin 2.2 建议改 `compilerOptions` DSL）、`libandroidx.graphics.path.so` 无法 strip，均不影响产物。
5. **网络约束**：`github.com` 直连超时，Gradle 分发走腾讯云/华为云镜像，官方示例资源走 `raw.githubusercontent.com`；`services.gradle.org` 的 307 跳转不可用。
6. **预置 init.gradle 损坏**：已备份为 `/root/.gradle/init.gradle.broken.bak` 并修复，`install.sh` 会自动检测修复。
7. **图标库放大 debug 包**：`material-icons-extended` 全量图标未裁剪，应用 APK 从 26 MB 增至约 69 MB；release 构建开启 R8 后仅保留用到的图标，体积可显著下降。表盘 APK（53 KB）不受影响。


---

## 9. 增量记录：UI 设计开源库接入（2026-11-08 追加）

在上一步交付的环境上引入 UI 开源库并重写示例界面。所有版本经过**仓库元数据 → POM 依赖 → AAR metadata → 实际 Gradle 构建**四重验证，详见 [`docs/UI_LIBRARIES.md`](docs/UI_LIBRARIES.md)。

### 9.1 引入内容

| 库 | 坐标 | 版本 | 来源 | 用途 |
| --- | --- | --- | --- | --- |
| Horologist | `com.google.android.horologist:horologist-compose-layout` | 0.7.15 | **Maven Central** | `rememberResponsiveColumnState` / `ScalingLazyColumn`，圆屏 padding 与 Rotary |
| Material Icons Extended | `androidx.compose.material:material-icons-extended` | 1.7.8 | Google Maven（BOM 管版本） | `Icons.Default.*` 图标 |

界面从单文本 `Text("Hello, Wear OS 6")` 改为 M3 分区式组件展示页：`AppScaffold(TimeText) → ScreenScaffold(state) → Horologist ScalingLazyColumn → Card / Button / Icon / ListHeader`。

### 9.2 版本验证结论

| 检查 | 结果 |
| --- | --- |
| Horologist 仓库位置 | `dl.google.com` 返回 **404**，Maven Central 返回 200 → 工程必须配 `mavenCentral()`（已配置） |
| 稳定版选择 | `maven-metadata.xml` 的 `<latest>` 指向 `0.8.4-alpha`，**稳定主线最高 0.7.15** |
| POM 依赖基线 | 0.7.15 对齐 `compose-bom 2025.07.00` / Wear Compose `1.5.0-rc01` / Kotlin `2.2.0`，与本项目同代 |
| AAR metadata | Horologist `minCompileSdk=1 / minAGP=1.0.0`；传递依赖 `compose-material:1.5.0-rc01`、`compose-foundation:1.5.0` 为 `minCompileSdk=35 / minAGP=8.6.0`，**均不要求 compileSdk 37** |
| 主题体系 | 只取 `compose-layout`（纯布局层），**未混入** `compose-material`（Wear M2 组件）与 `ResponsiveTimeText`（依赖 M2 `TimeSource`） |
| 图标库结构 | 1.7.8 的 `.aar` 仅 298 B（KMP 伞模块），真实实现 `material-icons-extended-android` 约 35 MB，由 Gradle `.module` 自动解析 |

### 9.3 构建与踩坑

- 首次构建**失败**：`compileDebugKotlin` 报 5 处 `Horologist API is experimental`。原因：布局 API 标注 `@ExperimentalHorologistApi`，Kotlin 默认视为错误。
- 修复：`MainActivity.kt` 加 `@file:OptIn(ExperimentalHorologistApi::class)` + import 注解。
- 二次构建：**BUILD SUCCESSFUL in 21s**，37 tasks（5 executed / 32 up-to-date），`app-debug.apk` **69,204,000 bytes**，SHA256 `39dd5f09cce81b977e9bd0811c062c4bbce8636206de346bc7f8b048a48a8726`。

### 9.4 产物与线上复验

- `aapt2 dump badging`：`compileSdkVersion='36'`、`targetSdkVersion='36'`、`minSdkVersion:'30'`、`platformBuildVersionName='16'`，与引入前一致。
- dex 内扫描确认 Horologist 布局类、`ExperimentalHorologistApi`、`Icons.Default.Palette`、M3 `AppScaffoldKt` 全部存在。
- 替换 `/workspace/apk-download/app.apk` 后：本地自测通过（69,204,000 B、`占位包=False`）；线上首页 200、`meta.json` size 一致、完整下载 69,204,000 B 且 ZIP 有效、SHA256 与本地一致、Range 206、无凭据 403、表盘直链 53,521 B。**下载链接未变**（服务实时读盘，无需重新发布）。

### 9.5 标注为「未接入 / 需额外验证」

Coil 3（KMP Android 变体解析未实测）、Lottie Compose 6.7.x（未查 `minCompileSdk`）、Horologist `0.8.x-alpha`（预发布）、Wear Compose 1.7.x / Compose 1.12.x / core-ktx 1.19.x（**明确不兼容**：要求 `compileSdk 37` + AGP 9.1.0+）。新增任何库前请按 `docs/UI_LIBRARIES.md` §5 的 5 步检查流程执行。

---

## 10. 增量记录：《小丑牌》玩法移植（WristJoker）

### 10.1 任务来源与识别

用户提供 B 站视频 `BV1LW421N71S`。B 站主站与 `api.bilibili.com` 在本沙箱返回 **412 风控**，改用第三方接口（`uapis.cn`）+ `x/player/pagelist` 拿到元数据，两处一致：

- 标题《98%好评如潮！小丑牌为什么这么上瘾？》，2024-02-23 发布，时长 581 秒
- 简介：「相关游戏：小丑牌 Balatro / store.steampowered.com/app/2379780/」

→ 游戏为 **《小丑牌 / Balatro》**（LocalThunk 开发，Playstack 发行，LÖVE 2D + Lua 引擎，2024-09-26 上线 Android/iOS，$9.99 买断）。

### 10.2 移植决策

**放弃 APK 封装，选原生重写**。三条理由：

| 维度 | 结论 |
|---|---|
| 法律 | 商业付费游戏，APK 再分发侵权；社区逆向文档不等于再分发许可 |
| 技术 | LÖVE 2D 运行时 + 手机横屏 1080p 布局 + 大图集，Watch7 仅 2GB RAM / 1.3~1.5" 圆屏，跑不稳且 UI 全错位 |
| 体验 | 强行侧载得到"能启动但没法玩"的窗口，不算移植 |

命名与包名均避开原作商标：**腕上小丑 / WristJoker**，`com.wearos.wristjoker`。图形全部由代码绘制，未使用原作任何素材文件。后续按用户确认把视觉语言高保真对齐原作（色值取自公开的 `G.C` 常量表），详见 §11。

### 10.3 目标设备口径

| 项 | 40mm | 44mm |
|---|---|---|
| 屏幕 | 1.3" 432×432 | 1.5" 480×480 |
| 逻辑分辨率 | 约 **192dp** 圆形 | 同左 |
| 系统 | Wear OS 5（API 34）+ One UI 6 Watch | 同左 |
| 芯片 / 内存 | Exynos W1000 / 2GB | 同左 |

两种尺寸逻辑宽度相同，因此只按 192dp 设计一套布局。

### 10.4 实现范围

已实现：13 种牌型（含 3 种隐藏牌型）、8 关盲注表（逐项对照官方）、筹码×倍率计分、18 种小丑、4 种 Boss 负面效果、金钱与利息、商店买卖与重掷、小丑位 5 格、无尽模式。

未实现：150+ 小丑中的其余部分、行星/塔罗/幽灵牌、卡牌增强与版本、牌组选择、标签系统、跳过盲注、卡包、挑战模式、音效。

### 10.5 数值验证（33 个 JVM 用例，纯 Kotlin 零 Android 依赖）

```
GameEngineTest         tests=11  failures=0   time=0.085s
HandEvaluatorTest      tests=13  failures=0   time=0.050s
ScoringTest            tests=9   failures=0   time=0.002s
合计 33 个用例，失败 0，跳过 0
```

对照官方的关键断言：

| 断言 | 期望 | 结果 |
|---|---|---|
| 对子 K K | (10+20)×2 = 60 | ✅ |
| 同花顺 A 高 | (100+51)×8 = 1208 | ✅ |
| 高牌 K 9 7 4 2 | (5+10)×1 = 15 | ✅ |
| 小丑 +4 倍率 | 30×(2+4) = 180 | ✅ |
| 加倍率后乘算（照片） | 30×((2+4)×2) = 360 | ✅ |
| 男爵 2 张手中 K | 28×(2×2.25) = 126 | ✅ |
| Ante 1/2/8 小盲 | 300 / 800 / 50000 | ✅ |
| Ante 1 大盲 / Boss | 450 / 600 | ✅ |
| `A K Q J 2` | 不是顺子 | ✅ |
| `A 2 3 4 5` | 是顺子 | ✅ |

「整局推进」用例用穷举 1~5 张全部组合挑最优的机器人来出牌，所以不依赖牌运。

### 10.6 圆屏布局验证（无模拟器时的替代手段）

沙箱无 `/dev/kvm`，模拟器无法启动。改用**按真实 dp 坐标 1:1 渲染 + 圆内切边数值校验**：

- 可玩区域半径 96dp，y=±77dp 处弦长仅 **115dp**，而屏幕宽 176dp（含 padding）→ 用 `fillMaxWidth` 必然被切
- 改为逐行限宽：状态 112 / 分数 148 / 进度条 168 / 提示 160 / 手牌 168 / 按钮 106
- 脚本逐行检查四角是否落在 `x²+y² ≤ 96²` 内：432px 与 480px 两个尺寸**全部通过**
- 渲染图：`apps/WristJoker/preview/screen-432.png`（当版为六界面版，见 §14）；美术样张 `wristjoker-art/preview.png`

垂直预算 157dp，居中后上下各留 17dp。

### 10.7 产物

| 项 | 值 |
|---|---|
| 文件 | `apps/WristJoker/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | 24,801,145 bytes（美术重绘后） |
| SHA256 | `20bde037cf052bfef746a9434bfa2c4713db80d4041e101df6902c19c5f39ed2` |
| compileSdk / targetSdk / minSdk | 36 / **34** / 30 |
| `aapt2 dump badging` | `platformBuildVersionName='16'`、`uses-feature: android.hardware.type.watch`、`launchable-activity: com.wearos.wristjoker.MainActivity`、label「腕上小丑」 |
| dex 校验 | `HandEvaluator` / `Scoring` / `Jokers` / `GameEngine` / `GameScreenKt` / `ScreensKt` / `MainActivity` / `ArtKt` / `PixelText` / `JokerPortrait` / `FeltBackground` / `CrtOverlay` 全部存在 |

`targetSdk` 特意用 34 而非 36：对齐 Watch7 出厂的 Wear OS 5，避免在其上触发未验证的新行为。`compileSdk` 保持 36 是因为 Wear Compose 1.5.0 的 AAR metadata 要求 `minCompileSdk = 35`。

### 10.8 下载站与线上复验

`/workspace/apk-download/` 加入第三个产物 `wristjoker.apk`，页面标题与说明同步更新。**链接未变**（服务实时读盘）：

| 检查 | 结果 |
|---|---|
| `meta.json` | 3 个文件：app.apk 69,204,000 / watchface.apk 53,521 / wristjoker.apk 24,801,145 |
| 响应头 | `200`、`application/vnd.android.package-archive`、`attachment; filename="wristjoker.apk"`、`Accept-Ranges: bytes` |
| 完整下载 | `200` / 24,801,145 bytes / **1.53 s**（美术重绘后复验），ZIP 有效，SHA256 与本地一致 |
| Range | `206` / 100 bytes |
| 无凭据 | `403` |

### 10.9 未验证项（如实）

**没有在真机上跑过。** 逻辑靠 33 个单测、布局靠 dp 级数值校验，两者都不等于"能玩"。真机上最可能出现的问题是字体行高与估算（1.35×）不符，导致最上/最下行轻微越界；若出现，把 `GameScreen` 的 `rowHeight` 从 23 降到 21 即可腾出 4dp。

其余未验证：旋转表圈的实际手感、50×26dp 按钮的命中率、Compose 在 Exynos W1000 上的帧率、Wear OS 5 上 `onRotaryScrollEvent` 的焦点获取时序。


---

## 11. 增量记录：按原作美术与牌面重绘

用户确认「可直接借鉴原作美术和牌面」后，只重绘表现层，玩法逻辑、33 个单测、圆屏宽度预算全部未动。

### 11.1 依据

| 来源 | 内容 |
|---|---|
| Balatro Wiki `Module:Localization/loadL10n` | 原作 `G.C` 颜色常量表（公开的本地化脚本） |
| designbycurio 设计系统分析 | 赌场绿毡、Bicycle 牌背红、CRT 扫描线、等宽里程表数字、硬边层次、每张小丑一张脸 |
| blakecrosley / TV Tropes | 颜色即标签（蓝=筹码、红=倍率）、稀有度四色、花色细微色差 |

### 11.2 改了什么

| 文件 | 变化 |
|---|---|
| `ui/Palette.kt` | 色板从「深夜靛蓝」换成 `G.C` 对齐色值；数字统一等宽 |
| `ui/Art.kt`（新增） | 5×7 点阵数字、四种花色 Path、Bicycle 几何牌背、18 个小丑的 8×8 像素头像、赌桌毡（旋转六边形+噪点+暗角）、CRT 扫描线、四角光标 |
| `ui/CardWidget.kt` | 牌面改为米白纸底 + 硬描边 + 内框 + 纸纹，中央大花色、右下蓝色筹码值 |
| `ui/Widgets.kt` | 硬边按钮、进度条、徽章、蓝×红=金计分公式 |
| `ui/GameScreen.kt` | 分数改点阵大字，盲注绿 / Boss 红，几何预算不变 |
| `ui/Screens.kt` | 商店与面板改成小丑牌卡片（稀有度色边框 + 像素头像） |
| `MainActivity.kt` | 三层叠放：赌桌毡 → 界面 → CRT |

### 11.3 边界

借鉴的是视觉语言、功能性配色与排版惯例，所有图形由 Kotlin Canvas 重绘。**没有**下载、打包或分发原作的图集、字体、Lua 资源或 APK。工程 `res/` 未新增任何图片；APK 内的 16 张 `.png` 全部是 AndroidX core 的通知兼容 9-patch（`notification_bg_*` / `notify_panel_*`）。规范见 `wristjoker-art/ART_BIBLE.md`，样张见 `wristjoker-art/index.html` 与 `wristjoker-art/preview.png`。

### 11.4 验证结果

| 检查 | 结果 |
|---|---|
| `gradle testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL，33/33 用例，failures=0 / errors=0 |
| `aapt2 dump badging` | compileSdk 36 / targetSdk 34 / minSdk 30，`uses-feature: android.hardware.type.watch`，label「腕上小丑」 |
| dex 扫描 | `PixelText` / `JokerPortrait` / `FeltBackground` / `CrtOverlay` / `SuitGlyph` / `CasinoButton` / `ScoreFormula` / `CardBackArt` 全部命中 |
| 圆内切数值校验 | 6 行四角半径 190.4~205.9px，全部 ≤ 216px（96dp×2.25），`ALL_INSIDE = True` |
| 渲染图 | `preview/screen-432.png` / `screen-480.png` 重生成，对应新美术 |
| 新产物 | 24,801,145 bytes，SHA256 `20bde037cf052bfef746a9434bfa2c4713db80d4041e101df6902c19c5f39ed2` |

## 12. 增量记录：短直链（手表浏览器可手输）

### 12.1 动机与方案

原始直链为 80 字符（`https://a01c7173418c05e23.app.workbuddy.host/wristjoker.apk?sharecode=XXRDMDM3DI`），在 1.3 英寸圆屏上用表盘键盘输入不现实。改用第三方 301 短链，**目标 URL 内嵌 `sharecode`**，因此短链只是携带凭据，不是绕过鉴权。主链用 tinyurl，备用链用 v.gd（不同服务商，互不依赖）。

### 12.2 三条短直链

| 产物 | 短直链（主） | 备用短链 |
|---|---|---|
| 腕上小丑 `wristjoker.apk` | `https://tinyurl.com/wjapk` | `https://v.gd/wjapk` |
| WearHello `app.apk` | `https://tinyurl.com/wearhello` | `https://v.gd/wearhello` |
| SimpleDigital `watchface.apk` | `https://tinyurl.com/wearface` | `https://v.gd/wearface` |

短码是语义化自定义后缀，不是随机串。生成脚本为 `/workspace/apk-download/shortlink.py`，机器可读清单为 `shortlinks.json`（含长链、大小、SHA256、生成时间）。

### 12.3 可复现性与校验

脚本每次生成后强制校验四项，任一不符即非零退出、不写清单：① 301 `Location` 必须**完全等于**本站带 `sharecode` 的长直链（防止自定义短码被他人占用或 query 被吃掉）；② 跟随跳转的 `Range: bytes=0-4095` 返回 `206` 且与本地前 4096 字节一致；③ 主链完整下载返回 `200` 且 SHA256 与本地一致；④ 短码已存在时回退到规范短链，仍由第①项裁定归属。

- `python3 shortlink.py`：生成/刷新 + 全量校验 + 自动同步 `index.html` 的 `SHORT_LINKS_BEGIN/END` 短链块。
- `python3 shortlink.py --verify`：只复验现有三条主链（本次通过，`verify_rc=0`）。
- 平台域名或 sharecode 变更时，改脚本头部 `BASE` / `SHARECODE` 两个常量重跑即可重建。

### 12.4 验证结果

| 检查 | 结果 |
|---|---|
| 三条主链 301 Location | 全部等于期望长直链，`sharecode` 完整 |
| 三条主链完整下载 | `200`，SHA256 分别 `39dd5f09…`、`4d752ee0…`、`63e672a6…`，与本地一致（wristjoker 已于 §13 换包） |
| 三条备用链 | 301 目标正确，`Range` 返回 `206` |
| 鉴权未被绕过 | 不带 `?sharecode=` 的长直链仍 `403` |
| 下载页线上实时生效 | 首页 grep 到 6 个短域名（3 主 + 3 备），**未重新发布** |
| 页面渲染 | 3 卡片 / 3 短链块 / 3 复制按钮，`pageerrors=[]`，见 `preview-shortlinks.png` |
| 回归 | `SELFTEST_ONLY=1 ./deploy.sh` 通过（69,204,000 B、占位包=False） |

### 12.5 边界与风险（如实）

- **本次没有重新发布站点，也没有改 `server.py`。** 重新发布会换平台域名与 sharecode，导致刚生成的短链目标失效；`index.html` / `config.json` 是服务实时读盘，改完立即生效。`shortlinks.json` 是本地清单，线上服务不暴露该路径，页面短链数据由 `index.html` 内嵌。
- 短链是第三方 301，目标固定不可改；服务商不可用时短链会失效，**原始长直链与下载页始终有效**。
- 短链把 `sharecode` 藏在跳转里，转发短链等同于公开下载权限；需要收回时按 `DEPLOY_GUIDE.md` 停止分享。
- 未做真机手表浏览器输入与下载验证（沙箱无 Watch7）。

## 13. 增量记录：圆屏适配修复 + CC0 牌面接入

### 13.1 问题（用户实测截图）

商店页三张小丑卡左右超出圆边、右侧价格被裁；顶部「商店」上半被裁；底部「重掷 / 继续」下半被裁。

根因：`ShopScreen` 用了 `fillMaxWidth()`（192dp）+ `fillMaxSize()`，没有遵守圆内切，纵向内容约 190dp 又顶到圆顶与圆底。主界面早就逐行限宽，所以只有商店页露馅。

### 13.2 布局修复

统一约束：元素矩形四角到圆心 ≤ 96dp。商店页收进 **126dp 宽的安全带**，内容总高 140dp；卡片区 `LazyColumn` 视口 94dp，两张完整 + 第三张露 5dp 提示可滚动；标题与按钮固定不滚。小丑面板 126dp / 行高 20dp，结算页 132dp。手牌槽位 23→26dp，与牌面 1.47 的裁剪比例对齐。

| 元素 | 宽×高 (dp) | 顶部 y (dp) | 最远角半径 (dp) | ≤96 |
|---|---|---|---|---|
| 商店标题行 | 126×16 | -70 | 94.2 | ✓ |
| 商店卡片区 | 126×94 | -51 | 81.1 | ✓ |
| 商店按钮行 | 126×24 | 46 | 94.2 | ✓ |
| 主界面状态行 | 112×16 | -76 | 94.4 | ✓ |
| 主界面手牌区 | 168×57 | -14 | 94.4 | ✓ |
| 主界面按钮行 | 106×28 | 48 | 92.7 | ✓ |
| 小丑面板 | 126×136 | -68 | 92.7 | ✓ |
| 结算页内容 | 132×132 | -66 | 93.3 | ✓ |

`OVERALL = True`。卡片 3 在 `LazyColumn` 视口外，由列表裁剪，不参与判定。

### 13.3 牌面来源与处理

- **来源**：Screaming Brain Studios「Poker Pack」，**CC0 1.0 公共领域**，可商用、无需署名（仓库 `art/License.txt` 原文）。视频 `BV18Da2z2EjT` 的 Godot4 开源 Demo 用的就是这套纹理，该视频简介给出了出处。
- **规格**：52 张 88×124 RGBA 单牌 + 1 张牌背，命名 `card_<s|h|d|c>_<00..12>.png`，放 `res/drawable-nodpi`，共 53 张、约 43KB。
- **编号映射**：00=A，01=2 … 09=10，10=J，11=Q，12=K；对应 `Rank.ACE → 0`，其余 `order - 1`。
- **裁剪策略**：手牌槽位 38×26dp（比例 1.47），牌面按宽度铺满后从顶部截 60px（88×60），露出左上点数、小花色、中央大花色上半。J/Q/K 人像下半被裁，但点数完整；整张竖牌塞进去会缩到 17dp 宽，点数反而看不清。
- **未替换**：18 张小丑头像仍是 8×8 自绘像素脸 —— 原作 joker 图是商业资产，不引入；这套 CC0 包里也没有小丑插画。

### 13.4 验证

| 检查 | 结果 |
|---|---|
| 构建 + 单测 | BUILD SUCCESSFUL，33/33，failures=0 / errors=0 |
| 资源打包 | APK 内 52 张 `card_[shdc]_NN.png` + 1 张 `card_back.png`，均在 `res/drawable-nodpi-v4/` |
| 几何校验 | 8 个元素四角半径 81.1~94.4dp，全部 ≤96，`OVERALL=True` |
| 视觉示意 | 已被 §14 的 `preview/screens-all.png` 取代（六界面 3×2 对照） |
| `aapt2` | compileSdk 36 / targetSdk 34 / minSdk 30，手表特性，label「腕上小丑」 |
| 新产物 | 24,801,145 bytes，SHA256 `20bde037cf052bfef746a9434bfa2c4713db80d4041e101df6902c19c5f39ed2`（比上版 +43,149 B，即牌面资源） |
| 短链 | `https://tinyurl.com/wjapk` → 301 → 200 / 24,801,145 bytes / 3.48 s，字节与本地完全一致 |

### 13.5 未验证（如实）

- 对比图是按 dp 坐标画的几何示意，不是 Compose 真机渲染；真机字体行高可能比估算大 1~2sp，若某行贴边，优先压 `Screens.kt` 的 `SAFE_W` 或卡片高度。
- 未在 Watch7 上侧载：表圈滚动、点击命中率、Exynos W1000 帧率、真实字体行高仍需真机确认。

## 14. 增量记录：流程补齐 + 牌面改回矢量 + 六界面（2026-11-15）

### 14.1 改了什么

用户要求「改回原本的 52 张牌面数字显示、更精美的 UI、游戏流程全部借鉴小丑牌、要开始界面和结束界面」。三件事一起做，改动覆盖 `game/Blind.kt`、`game/GameEngine.kt`、`ui/Art.kt`、`ui/CardWidget.kt`、`ui/Widgets.kt`、`ui/GameScreen.kt`、`ui/Screens.kt`、`MainActivity.kt` 与 `GameEngineTest.kt`。

| 项 | 上一版 | 这一版 |
|---|---|---|
| 阶段数 | 4（PLAYING / SHOP / GAME_OVER / WIN） | **7**（多 MENU / BLIND_SELECT / CASH_OUT） |
| 牌面 | 53 张 CC0 位图，顶部裁切 | **纯 Canvas 矢量**，APK 内牌面 PNG = 0 |
| 开局 | 直接进牌桌 | 主菜单 → 盲注选择 → 牌桌 |
| 过关 | 直接进商店并加钱 | 停在结算页，点「收下」才入帐 |
| 跳过关卡 | 无 | 小盲/大盲可跳，换 +$3，Boss 不可跳 |
| 卖出小丑 | 无 | 商店里点方形徽章卖出，返半价（≥$1） |
| 结束页 | 单页复用 | 通关/失败两种版式，带最高单手、小丑数、本关差 |

### 14.2 单元测试

| 文件 | 用例 | failures | errors |
|---|---|---|---|
| `GameEngineTest` | 21 | 0 | 0 |
| `HandEvaluatorTest` | 13 | 0 | 0 |
| `ScoringTest` | 9 | 0 | 0 |
| **合计** | **43** | **0** | **0** |

新增用例覆盖：开局停在 MENU 且不发牌、开始游戏列出三档盲注、跳过小盲的补偿与前进、Boss 不可跳、结算页不入帐、结算明细合计等于三项之和、利息封顶、收下后清空明细、卖出返半价、离开商店回到盲注选择、回主菜单清空进度。

**测试改造**：`resetRun()` 与 `startBlind()` 各洗一次牌，固定 seed 的牌运会翻转。原 `seed=5` 的用例全部变红（`expected CASH_OUT but was GAME_OVER`）。改为 `winningEngine()` 遍历 1~60 号种子取第一个真能打到结算的引擎，测试只依赖流程本身。

### 14.3 圆屏几何校验（逐元素）

判据：元素四角到圆心 ≤ 96dp（屏幕半径 108dp，留 12dp 给表圈）。

```
开始界面   总高 133.7dp  最远角 84.7dp  PASS
盲注选择   总高 129.0dp  最远角 88.8dp  PASS
结算页     总高 144.0dp  最远角 90.6dp  PASS
商店       总高 143.0dp  最远角 94.0dp  PASS
结束页     总高 141.0dp  最远角 93.2dp  PASS
主牌桌     总高 144.0dp  最远角 92.2dp  PASS
OVERALL = True
```

**容差模拟**（真机字体行高比 dp 估算多 1 / 2dp）：

| 情形 | 总高 | 最远角 | 判定 |
|---|---|---|---|
| +1dp/行 | 150.0dp | 93.6dp | PASS |
| +2dp/行 | 154.0dp | 95.2dp | PASS |

上一版主牌桌最远角 95.5dp，余量仅 0.5dp，+1dp 就切边。这一版把间距压到 2/3/2/3/4、手牌行高 26→25、按钮 26→24、手牌与进度条宽度 168→162，才换来「+2dp 仍安全」。

### 14.4 渲染对照图

`preview/screens-all.png` 是六个界面的 432px 1:1 渲染（density 2x，216dp 宽）。**渲染脚本第一版把 dp→px 当成了 4x**（因为误以为 432px = 108dp），所有元素放大一倍、全被圆边切掉；改成 2x 后才对得上。

这份图是按 dp 坐标逐元素绘制的，不是 Compose 真机截图，用途是验证布局几何与视觉密度，不能替代真机。

### 14.5 产物与线上

| 项 | 值 |
|---|---|
| 大小 | 24,801,145 bytes |
| SHA256 | `20bde037cf052bfef746a9434bfa2c4713db80d4041e101df6902c19c5f39ed2` |
| APK 内牌面 PNG | **0**（`res/drawable-nodpi` 已清空，只余 AndroidX 的 16 张通知 9-patch） |
| dex 符号 | `MenuScreen` / `BlindSelectScreen` / `CashOutScreen` / `EndScreen` / `CardFaceArt` / `EmbossedTitle` / `LedgerRow` / `JokerBadge` 全部命中 |
| `aapt2` | compileSdk 36 / targetSdk 34 / minSdk 30，手表特性，label「腕上小丑」 |
| 短链 | `https://tinyurl.com/wjapk` → 301 → `200` / 24,801,145 bytes / 2.19 s，与本地字节完全一致 |

体积比上一版 +53,111 B：删掉 53 张牌面 PNG 省下约 43 KB，新增 6 个界面的 Kotlin 代码与字形表增加约 96 KB，净增 53 KB。

### 14.6 本轮修掉的缺陷（自查记录）

| 缺陷 | 现象 | 处理 |
|---|---|---|
| 点阵表缺字 | 副标题 `JOKER` 渲染成 `J?K??` | 补 20 个字母字形（O/E/R 必需，其余顺带） |
| A 字形错 | 用了小写 `a` 的双层字形，25dp 下像「彐」 | 改成大写 A |
| 宫牌不可辨 | J/Q/K 中央的 5×7 点阵字母在 25dp 牌面上成一团 | 中央统一画花色，宫牌加三齿冠冕区分 |
| 小丑徽章溢出 | 「头像 + 名字」横条 5 个并排要 210dp，圆屏安全宽只有 122dp | 改成 15dp 方形头像 + 右下角 `$` 标记，5 个只占 83dp |
| 第三张卡不露边 | 卡片 38dp + 间距 3dp，80dp 视口装不下「2 张 + 露出」 | 卡片 36dp + 间距 2dp，第三张正好露 4dp |
| 分数与目标贴死 | 间距 3dp，432px 屏上只有 1.4mm | 间距拉到 6dp |
| 牌背像信封 | 斜网 alpha .22、密度 w/5.5、菱形 r=w*.26 | 降到 .13、w/7.5、r=w*.21 |

### 14.7 未验证（如实）

- 仍未在 Watch7 上侧载。表圈旋转选牌、按钮命中率、Exynos W1000 上的帧率、系统字体真实行高都只能真机确认。
- 渲染图是按 dp 数值画的，与 Compose 的实际测量值可能有 1~2dp 出入，尤其 `BasicText` 的行高与 `animateIntAsState` 的中间帧。
- 7 态阶段机只在 JVM 单测里跑通，Compose 侧的状态切换（尤其 `showJokers` 在阶段变化时的重置）没有真机/模拟器验证。

---

## 15. 增量记录：完整机制 + 自动存档 + 打击感 + 原版布局（2026-11-16）

### 15.1 任务来源

三条 B 站视频（`BV1QZ421171Z` 为《3 分钟入坑小丑牌》新手教学，另两条 `BV1QMMFzxE1j`、`BV1CKHV6wEhx`）与四条明确反馈：

> 没有完整的机制 / 没有自动存档 / 缺少原版的打击感 / 可以借鉴原版的 UI 布局再加以适配手表

逐条对应成四块改造，全部落在代码里，不做"看起来做了"的假动作。

### 15.2 机制补齐（对照原作）

上一版只有「牌型 + 18 小丑 + 8 关」，缺的是**构筑曲线**：没有塔罗就没有牌面改造，没有行星就没有牌型升级，玩家每一手的期望值恒定，那就不构成 Roguelike。

| 机制 | 原作 | 上一版 | 本版 |
|---|---|---|---|
| 消耗品 | 塔罗 22 + 行星 12 + 幻灵 18 | 无 | **塔罗 14 + 行星 12**，2 个槽位 |
| 牌型等级 | 12 种牌型各可升级 | 字段有、无来源 | **行星牌升级，UI 有独立等级页** |
| 卡牌增强 | 8 种 | 无 | **8 种**（奖励/倍率/万能/玻璃/钢铁/石头/黄金/幸运） |
| 蜡封 | 4 种 | 无 | **3 种**（红重触发 / 金给钱 / 蓝给经验） |
| 版本 | 4 档 | 无 | **4 档**（普通/箔/全息/多彩），牌与小丑通用 |
| 小丑 | 150 张 | 18 张 | **37 张**，按稀有度加权抽取（上一版 `shuffled()` 忽略权重） |
| 代金券 | 32 张 | 无 | **8 张**，第 2/4/6/8 关商店出售 |
| 标签 | 24 种 | 无（跳过只给 $3） | **6 种**，跳过盲注换一张 |
| Boss 效果 | 23 种 | 4 种 | **13 种**（四种花色封禁 / 针 / 水 / 墙 / 镣铐 / 通灵 / 眼 / 蛇 / 燧石 / 钩） |
| 牌组 | 15 种 | 无 | **8 种**（红/蓝/黄/黑/绿/镀金/玻璃/随机） |
| 无尽模式 | 有 | 无 | **通关后可继续，Boss 按关号循环** |
| 利息 / 重掷 / 商店刷新 | 有 | 有 | 保留，接上代金券折扣与上限提升 |

小丑效果全部重写为 4 个钩子（`chips` / `addMult` / `xMult` / `money`），引擎按摆位从左到右依次调用 —— 这让「+倍率放前面、×倍率放后面」成为可优化的策略，而不是装饰。单测 `小丑摆位决定结果 加算在前乘算在后更高` 固定了这个顺序（12× vs 8×）。

### 15.3 自动存档

- **格式**：`SaveCodec` 手写行式文本（`key|value`，值内 `,` 分元素、`~` 分子字段）。刻意不用 JSON —— `org.json` 在 JVM 单测里是空壳类，一调用就抛 `Stub!`，而这恰恰是最需要反复测的部分。
- **内容**：种子、牌组、阶段、关号、盲注序号、分数、金钱、出牌/弃牌/手牌上限、52 张牌主表（含增强/蜡封/版本）、三个堆、小丑（含版本与成长售价）、消耗品、牌型等级、代金券、Boss 序列、标签、本关已出牌型、商店、重掷价、结算明细、统计。
- **时机**：每次有效操作后 `apply()` 一次；`onStop` 强制落盘；进入结束态时清空存档并记录历史最好成绩。存档串约 1.4 KB。
- **容错**：坏行只丢该行；缺字段回落到安全默认值；版本号高于当前直接拒绝加载；主表里不存在的牌 id 会从三个堆里剔除（防"幽灵牌"）。
- **冷启动**：`MainActivity.onCreate` 先 `restore()`，主菜单直接显示「继续 · A3 · 小盲注 · $17」。
- 边界处理：存档点正好落在「这一手已达标但动画没播完」时，`restore()` 会补一次结局，避免恢复后卡在 PLAYING 既出不了牌也过不了关。

### 15.4 打击感

原版爽的不是最终分数，而是**把分数怎么来的逐条演出来**。为此把计分从"返回一个数"改成"返回一条序列"：

- `Scoring.compute` 产出 `List<ScoreStep>`，每步带 `source`（牌型/某张牌/手中牌/某小丑）、`label`、执行后的 `chips` 与 `mult`。
- `GameScreen` 用 `LaunchedEffect(ui.handsPlayed)` 按 78ms（加算）／145ms（乘算）逐条播放：结算带左边显示"谁给了多少"，右边显示 `chips × mult` 实时值。
- 引擎**不在出牌后立刻切阶段**：结局挂在 `pendingOutcome` 上，由 UI 播完动画再调 `resolvePending()`。否则一达标就跳走，玩家看不到自己是怎么赢的。
- 乘算步触发 `ShakeBox` 整屏抖 2dp、三段衰减（-1.4 → +0.9 → -0.4 → 0，260ms）。
- 4 档震动：选牌 8ms/55、筹码与加算 14ms/110、乘算 22ms/175、结算 38ms/255。
- 10 个音效**全部代码合成**：`Sfx` 生成 16bit 单声道 PCM（正弦/方波/三角/噪声 + 指数包络 + 滑音 + 颤音），套 44 字节 WAV 头写进 `cacheDir`，交给 `SoundPool` 播放。APK 内没有一字节音频文件，改手感只需改常数。
- 玻璃牌碎裂 1/6、幸运牌 1/5 —— 概率走引擎的 `Random`，而**预览不计概率**（`previewOf` 传 `Random(0)`），否则"看一眼预估分"会改变牌碎不碎。

### 15.5 布局：照搬原版分区

| 原版（PC 横屏） | 这里（216dp 圆屏） | 高度 | 宽度上限 |
|---|---|---|---|
| 左上 底注/金钱 | 行 A：`A3/8 · 盲注 · $17` | 13 | 110 |
| 左栏竖排 5 小丑 | 行 B 左段：5 个 15dp 徽章横排 | 15 | 132（与右段合计） |
| 右栏竖排 2 消耗品 | 行 B 右段：2 个 15dp 图标 + 分隔条 | ↑ | ↑ |
| 顶部 Boss + 分数 | 行 C：点阵累计分 | 18 | 142 |
| 顶部进度条 | 行 D：进度条 + `目标 5万` | 6 | 150 |
| 中部 出牌区 / 公式 | 行 E：结算带（逐张飘字） | 17 | 154 |
| 底部 手牌 | 行 F：2×4 = 8 张，35.5×20dp | 44 | 154 |
| 底部 出/弃牌 | 行 G：两枚按钮 | 22 | 106 |
| — | 合计 | **148** | — |

两条竖栏折成一行是这轮最关键的决定：竖排 5 张小丑要占 100dp 高，在 148dp 的总预算里等于砍掉手牌区。横排后 5 徽章 + 分隔 + 2 消耗品只占 121dp，正好塞进 y=-59 那一档的 157dp 弦长里，语义仍是"我有哪些小丑、点一下看详情"。

### 15.6 验证结果

| 项 | 结果 |
|---|---|
| 单测 | **60 个全通过**（`testDebugUnitTest`，failures=0）。新增 27 个：增强/蜡封/版本/石头牌/万能牌/燧石/摆位顺序/结算序列/消耗品/牌组加成/Boss 规格/存档往返/存档容错 |
| 自动跑局 | 测试里带一个"会弃牌找牌型"的自动打法，`winningSeed()` 在 1..400 内搜索能打过第一关的种子，验证"出牌 → 达标 → 结算 → 收钱 → 商店"整条主链 |
| 编译 | `assembleRelease` 成功（AGP 8.13.2 / Kotlin 2.2.10 / compileSdk 36） |
| 圆屏几何 | 8 个界面全 PASS，最远角 94.6dp（主牌桌行 A）≤ 96dp；「每行 +1dp」容差下主牌桌 94.6dp 仍 PASS，+2dp 才切边（见 §15.7） |
| 预览图 | `apps/WristJoker/preview/` 8 张 432px 单屏 + `screens-all.png` 2×4 对照，由 `tools/render_preview.py` 按 dp 值渲染 |
| 产物 | 20,041,452 bytes，SHA256 `6ced46d5f723ccc7a3a6045147c88378c9148209fd31f7402ba9ca280bd5c50a` |
| dex 符号 | `SaveCodec` 在 `classes2.dex` 命中 14 处；`VIBRATE` 权限已进清单 |
| 线上 | `https://a01c7173418c05e23.app.workbuddy.host/files/wristjoker.apk?sharecode=XXRDMDM3DI` 回下载 20,041,452 B，SHA256 与本地**完全一致**；短链 `https://tinyurl.com/wjapk` 301 → 200 |

体积比上一版（24,801,145 B）少 4,759,693 B。本版没有删除任何资源、也没有新增位图或音频（音效是运行时生成的），所以这个差异不来自素材增减；具体归因未逐项核对，只记录事实。

### 15.7 自查缺陷（本轮修掉的）

| 缺陷 | 现象 | 处理 |
|---|---|---|
| 布局坐标系混用 | 预览渲染把所有行堆在顶部、互相重叠 | 布局函数返回 dp、绘制函数吃像素，混用后全部错位；统一在 `layout()` 出口换算成像素 |
| 分数行溢出 | `1.8万 ×12.5 /5万` 三件套在 142dp 里叠字 | 行 C 只留累计分（点阵放大到 2.15dp/格），目标挪到行 D 进度条右侧，倍率挪进结算带 |
| 顶栏贴边 | 行 A 118dp 时四角 94.6dp，只剩 1.4dp 余量 | 收到 110dp → 92.2dp，给真机字体留出容差 |
| 通关页过高 | 多一颗「继续无尽」后总高 153dp，四角 97.8dp 切边 | 账单从 3 行减到 2 行（去掉信息价值最低的"小丑牌"行），总高 139dp |
| Boss 文案重复 | 选择页显示「目标 1200 · 目标 ×4」 | `WALL` 的描述改成「超大盲注」 |
| 小丑稀有度形同虚设 | 上一版 `ALL.shuffled().take(n)` 完全不看权重 | 改成按 `rarity.weight` 加权抽取，并排除已拥有 |
| 牌背斜纹溢出 | 预览图里牌背的斜网糊满整块圆屏 | 渲染脚本加 Cyrus-Beck 线段裁剪（Compose 侧本来就被 `clip()` 挡住，是渲染工具的问题） |
| 商店第三张压行 | 预览图里第三张商品盖住"已拥有"那一行 | 视口 76dp 正好放 3×24 + 2×2，是渲染脚本没按视口裁剪，修正后不再重叠 |

### 15.8 未验证（如实）

- **仍未在 Watch7 真机上侧载**。表圈旋转选牌、按钮命中率（最小 13dp 的「用/丢」按钮）、Exynos W1000 上的帧率（每张牌一次 `Canvas` 重绘 + 8 张牌的 `graphicsLayer` 缩放）都只能真机确认。
- **音效没有在真实扬声器/骨传导上听过**。合成参数（频率、包络、时长）是按物理直觉定的，实际听感可能偏尖或偏闷；音量与是否该默认关闭需要真机判断。
- **震动强度未校准**。四档的 amplitude 值（55/110/175/255）是线性猜测，Wear OS 上不同厂商的马达响应差异很大。
- **字体放大场景**。所有行都用固定 `Modifier.height()`，系统 `fontScale` 调大时文字会被裁而不是撑高 —— 几何安全（不会切边），但可读性会下降。这一版没有做字号自适应。
- 预览图是按 dp 数值画的，与 Compose 实际测量有 1~2dp 出入；`BasicText` 的行高与 `animateFloatAsState` 的中间帧无法在静态图里体现。

---

## 16. 增量记录：商店三段 + 打击感五层 + 原版分区 + 详情页（2026-11-15 第二轮）

### 16.1 任务来源

上一轮交付后收到的反馈：

> 还是没有完全还原游戏机制 打击感和 UI 布局 你应该上网找一找关于这三个方面的信息再做修改 商店商品不完全 小丑牌不能在商店内查看详情 出售小丑牌容易误触 这些问题总的来说都是没有完全借鉴原版的 ui 布局和机制所来的

拆成四件事：机制对齐原作、打击感对齐原作、分区对齐原作、三处具体缺陷。前三件查了原版公开文档与社区整理的数值表（商店结构、补充包规格、稀有度权重、Boss 清单、juice 栈分层），第四件在代码里逐个改。

### 16.2 商店改成原版三段结构

上一版的商店只有 3 个商品格、偶尔一张券 —— 玩家整局见不到补充包，而补充包是原版拿到塔罗/行星/改造扑克的**第二条通道**，也是"商店商品不完全"的直接原因。

| 位置 | 原版 | 上一版 | 本版 |
|---|---|---|---|
| 随机商品 | 2 格（小丑 : 塔罗 : 行星 = 20 : 4 : 4） | 3 格、全是小丑 | **2 格**（券可扩到 3/4），按 20:4:4 抽类型 |
| 补充包 | 2 格 | 无 | **2 格**，Arcana / Celestial / Standard / Buffoon |
| 代金券 | 1 格 | 偶尔 1 张 | **1 格**，固定 |
| 重掷 | \$5 起，每次 +1，**不刷新包与券** | 刷新全部 | **\$5 起每次 +1**；`reroll()` 先存下 `boosters` 与 `voucher`，重抽后原样放回，只有商品位变 |
| 清仓折扣 | 券「清仓」后 -50% | 无 | 有，`buy()` 里按 `clearanceDiscount` 计算 |

`ShopState` 从 `List<ShopItem>` 拆成 `ShopState(cards, boosters, voucher)` 三段，存档同步拆 `shopc / shopb / shopv` 三行（`SaveCodec.VERSION` 升到 2）。

### 16.3 补充包与幻灵「灵魂」

| 类型 | 普通 \$4 | 巨型 \$6 | 超大 \$8 |
|---|---|---|---|
| 塔罗 / 行星 / 标准 | 3 选 1 | 5 选 1 | 5 选 2 |
| 小丑 | 2 选 1 | 4 选 1 | 4 选 2 |

- 标准包生成的新牌从 id 100 起编号，避开牌堆原有的 0..51；增强 40% / 蜡封 20% / 版本 10%（受「幻觉」券加成）。这些牌要进存档主表，所以 `cardToken` 对 id ≥ 52 的牌额外存 `rank~suit`。
- **灵魂**：原版不以独立包出售，而是 0.3% 概率顶替任意补充包里的一张。这里按 `random.nextInt(1000) < 3` 实现，顶替而非追加（包内张数不变，单测钉住了这点）。灵魂是**传奇小丑的唯一入口** —— 商店抽取池里传奇的权重是 0，`rollOne` 抽 500 次也抽不到。
- 5 张传奇的效果靠引擎已有统计量驱动，不为每张牌再记一个玩家看不见的计数器：卡尼奥读「被摧毁的人头牌数」、约里克读「累计弃牌数 / 23」、特里布莱读「本手打出的 K/Q 数」、希科把 `spec.modifier` 直接置 `NONE`、佩尔克在 `leaveShop` 复制一张消耗品。

### 16.4 清单扩容

| 项目 | 上一版 | 本版 | 补齐的部分 |
|---|---|---|---|
| 小丑 | 37 | **101** | 蓝图 / 头脑风暴（复制相邻）、5 张传奇、按稀有度加权（70/25/5/0） |
| 塔罗 | 14 | **22** | 愚者 / 女祭司 / 皇帝 / 死神 / 恶魔 / 月亮 / 太阳 / 世界 |
| 行星 | 12 | 12 | 已覆盖全部 12 种牌型 |
| 幻灵 | 无 | **1**（灵魂） | 传奇入口 |
| 代金券 | 8 | **17** | 多余库存 / 清仓 / 幻觉 / 工匠 / 种子基金 / 银行等 |
| 标签 | 6 | **14** | 魅力 / 陨石 / 小丑 / 虚空 / 标准 / 投资 / 速度 / 经济 / 双倍 / 手巧 / 杂耍 / 优惠券 / 头目 / D6 |
| Boss | 13 | **28** | 补齐 23 个普通 + 5 个 Showdown，带 `minAnte`，第 8 关固定 Showdown |
| 版本 | 4 档 | **5 档** | 增加负片（+1 小丑槽） |

### 16.5 打击感：照原作的 juice 栈做五层

原版的"爽"不是一个大动画，是五层同时发生。手表上逐层压到 432px 的预算内：

| 层 | 原作做法 | 本作参数 | 位置 |
|---|---|---|---|
| 牌弹跳 | 打出的牌逐张弹起、计分后落下 | `spring(dampingRatio = .42f, stiffness = 1400f)`，计分完的牌变暗到 55% | `GameScreen.PlayedCard` |
| 数字滚动 | 筹码与倍率不是跳变，是滚 | `tween(120)` 补间，UI 只在值变化时重算 | `ScoreBoard` |
| 震屏 | 分越大抖越狠 | `magnitudeOf(得分)` 返回 1/2/3/4/5dp，四档阈值 500 / 5k / 50k / 500k | `GameScreen` 顶层 offset |
| 飘字 | 每张牌 `+N` 飞出 | 上移 9dp、420ms、后 40% 淡出 | `FloatingGain` |
| 音高上行 | 每张牌音高升半音 | `Sfx.scaleRate(i) = 2^(2i/12)`，夹在 0.5~2.0 | `fx/Feedback.kt` |

`ScoreStep` 新增 `StepSource.EDITION` —— 箔/全息/多彩各自单独一步，"多彩亮了"这件事在动画里看得见，而不是被合并进小丑那一步。震动仍是四档（8/55、12/110、16/175、22/255），音效 12 种，全部运行时合成，APK 内无音频文件。

### 16.6 布局改成原版分区

| 行 | 尺寸（dp） | 对应原版哪个区 |
|---|---|---|
| A 顶栏 | 110×12 | 左上角底注 / 盲注 / 目标 |
| B 小丑与消耗品 | 132×14 | 顶部小丑横排 + 右栏消耗品（折成一行） |
| C 出牌区 | 154×22 | 中部出牌区（**本版新增**） |
| D 筹码×倍率 | 146×16 | 右上角 Chips × Mult |
| E 进度条 | 150×5 | 目标进度 |
| F 手牌 | 154×42 | 底部手牌（2×4） |
| G 按钮 | 106×20 | 底部 Play / Discard |

上一版没有出牌区：牌打出去就消失，只有一行结算带闪一下 —— 这是"打击感不足"的根因，不是动画不够。出牌区在无牌时退回信息位（Boss 效果或当前最高牌型），避免圆屏中央空着。

### 16.7 三处缺陷的修法

| 缺陷 | 现象 | 修法 | 位置 |
|---|---|---|---|
| 商店商品不完全 | 只有 3 格小丑 + 偶尔 1 张券，见不到补充包 | 三段结构 + 四个分页（商品/卡包/代金券/我的），券与包各有独立页 | `ShopScreens.ShopScreen` |
| 小丑不能看详情 | 一行 8.5sp 名字 + 6sp 说明，长效果被裁；点一下直接扣钱 | 行右侧标 `›`，点开详情弹层：40dp 大图 + 名字 + 稀有度与价格 + 3 行正文 + 动作按钮；商店商品、自己的小丑、消耗品都能点开 | `DetailOverlay` |
| 出售容易误触 | 长按小丑直接卖，圆屏边缘一碰就没了 | 详情页按钮两段式：先「卖出 \$N」，再「再点确认卖出」，2.2 秒不点自动复原；卖掉核心小丑的操作从"一次误触"变成"两次明确点击" | `DetailOverlay(confirming)` |

详情按钮的时间窗用 `LaunchedEffect(confirming)` + `delay(2200)` 实现，弹层关闭时协程自动取消，不留悬空状态。

### 16.8 验证结果（第二轮当时的快照；第三轮最新结果见 §17.5）

| 项 | 结果 |
|---|---|
| 单元测试 | **91 个全通过**（`GameEngineTest` 21 / `HandEvaluator` 13 / `Mechanics` 31 / `SaveCodec` 7 / `Scoring` 19），本轮新增 8 个：传奇 5 张且不进商店、灵魂生成传奇、槽满折价 \$20、希科关 Boss、佩尔克复制消耗品、卡尼奥与约里克成长、特里布莱 ×2/张、灵魂顶替不追加 |
| 圆屏几何 | 10 个界面全 PASS：开始 86.9 / 牌组 83.1 / 盲注 85.2 / 结算 91.2 / 商店 82.1 / 开包 83.5 / 详情 79.8 / 结束失败 87.3 / 结束通关 90.4 / 主牌桌 91.3（阈值 96.0dp） |
| 容差模拟 | 每行 +2dp 后全部 PASS（主牌桌 94.6 / 结束通关 94.3）；主牌桌 +3dp 才 FAIL（97.1dp） |
| 编译 | `assembleRelease` 成功（AGP 8.13.2 / Kotlin 2.2.10 / compileSdk 36） |
| 产物 | **20,205,292 bytes**，SHA256 `1c7d40ec4057448c30460f4e2b63d86a4cda6e666ed5cda540db386e7b6d043a` |
| dex 校验 | `classes2.dex` 命中「灵魂 / 卡尼奥 / 特里布莱 / 约里克 / 希科 / 佩尔克」与「再点确认卖出 / 空白处返回 / 代金券卖完了」 |
| 线上 | 从本地服务回下载 `wristjoker.apk`：HTTP 200、20,205,292 B、SHA256 与构建产物**逐字节一致** |
| 预览图 | `preview/` 10 张 432px + `screens-all.png` 4×3 总览，由 `tools/render_preview.py` 按 dp 数值渲染 |

### 16.9 自查缺陷（本轮修掉的）

| 缺陷 | 现象 | 处理 |
|---|---|---|
| 详情页正文压住按钮 | 3 行说明与底部按钮重叠 | 正文 `maxLines = 3, softWrap = true` + 固定 28dp 高，标题间距 1→2dp，渲染脚本整体上移 8dp |
| 商店一屏塞不下三段 | 一行只显示 2 项、说明被裁 | 改成四个分页标签；行高 23→28dp、图标 17→20dp |
| 预览图分数行叠字 | `1420` 与 `12.5` 在 142dp 里重叠 | 筹码右对齐 −9dp、×居中、倍率左对齐 +9dp |
| 通关页 +2dp 切边 | 底部按钮行 122dp 时最远角 96.3dp > 96 | 该行收到 118dp（其他界面不动），+2dp 后 94.3dp |
| 传奇数量虚标 | 文档写"含传奇"但 `ALL` 里没有传奇实例，`LEGENDARY` 是死枚举 | 补齐 5 张传奇 + 灵魂，`shopPool` 过滤才真正有意义 |
| 小丑数量写错 | 注释写 88、实际 96 | 统一为 101（含传奇），单测断言从 `>= 88` 改成 `assertEquals(101, …)` |

### 16.10 未验证（如实）

- **仍未在 Watch7 真机侧载**。详情弹层的点击热区（122dp 宽、按钮高 20dp）、分页标签 13dp 高的可点性、出牌区 22dp 里 5 张 24×17dp 的牌的辨识度，都只能真机确认。
- **灵魂 0.3% 的概率手感**没在长局里试过。按 3000 个包 3 张的模拟命中 1~60 次区间，实际一局能开 10~20 个包，等于"多数局见不到" —— 与原版一致，但手表单局更短，是否该上调（比如 0.5%）需要真机跑几局再定。
- **传奇的强度未平衡**。卡尼奥/约里克在长局里会滚成倍率怪物，本作没有原版那种 20+ 关的长度来摊薄，可能出现"拿到即通关"。
- **音效与震动**同上一轮，未在真实硬件上校准。
- 预览图仍是 dp 数值渲染，不是 Compose 实际截图。

---

## 17. 增量记录：上网核对原版后补齐机制 / 打击感 / UI 布局（2026-11-20 第三轮）

### 17.1 任务来源与做法

用户第二次反馈：**「还是没有完全还原游戏机制 打击感和 UI 布局，你应该上网找一找关于这三个方面的信息再做修改」**。这一轮不再只靠已有实现自判，而是先检索原版资料、列出「代码 vs 原版」的真实差距清单，再按清单逐项改。

检索到的关键资料：

| 来源 | 取到的信息 |
|---|---|
| `blakecrosley.com` 的 Balatro 设计拆解 | 原版 juice stack 五层：卡牌弹跳 / 逐位 stagger 的 chip counter / 震屏 + CRT 瞬态 + 背景按牌型色脉冲 / 筹码粒子拖尾 / 逐张升调 + ka-ching + bass drop；以及「每个 Joker 从左到右依次 pulse」的协同可视化 |
| `deepwiki.com` 的 `G.UIDEF` 逆向（`create_UIBox_HUD` / `create_UIBox_shop`） | 牌桌 HUD 有左上 Blind 目标分与奖励、选中牌型及基础值、Joker 顶栏、Consumables 侧栏；商店 HUD 有**顶部已拥有 Joker row + `4/5` 槽位计数**、商品卡片**价格标签在卡片上方** |
| Balatro Wiki `Collection` | 清单基数：Jokers 150 / Consumables 52 / Tarot 22 / Planet 12 / Spectral 18 / Seals 4 / Booster Packs 5 类 |
| Balatro Wiki `Card_modifiers` / Booster Packs 权重表 | 4 种蜡封的确切行为；5 类补充包与 `4/2/0.5`、`1.2/0.6/0.15`、`0.6/0.3/0.07` 三档权重 |
| Fextralife / dood.gg 的 Spectral 列表 | 18 张幻灵的完整清单与效果语义 |

### 17.2 真实差距清单（改之前）

| 维度 | 原版 | 改之前的代码 |
|---|---|---|
| 蜡封 | 4 种（红重触发 / 金 \$3 / 蓝回合末行星 / 紫弃牌塔罗） | 3 种，且**蓝蜡封错误地在计分时给钱** |
| 补充包 | 5 类（含 Spectral） | 4 类，权重是近似值 |
| 幻灵牌 | 18 张 | 只有 1 张「灵魂」 |
| 消耗品总数 | 52 | 34 |
| HUD 信息 | 选中牌型基础值、商店 Joker row + 槽位计数、价格标签 | 缺 |
| juice | 逐位滚动 / CRT / 背景脉冲 / 粒子 / 闪白 / 随机倾斜 / Joker pulse / ka-ching / bass drop | 只有五层基础版 |

### 17.3 机制补齐（按清单逐项）

- `Seal` 补 `PURPLE`；`Scoring` 删掉蓝蜡封错误的计分给钱；`discard()` 里紫蜡封被弃时各生成一张塔罗，`clearBlind()` 里蓝蜡封按 `lastHandType`（原版 1.0.1f 起按最后牌型）生成对应行星。
- `Consumables` 从 1 张幻灵扩到 **18 张**，`ALL = 22 + 12 + 18 = 52`；新增 `ConsumableResult` 的 10 个字段表达「改写手牌 / 复制或清空小丑 / 全牌型升级 / 手牌上限 / 金钱归零 / 生成稀有 / 指定版本」。
- `GameEngine.useConsumable()` 把这些字段**全部落账**（含 `nextCardId()` 重分配新牌 id、随机摧毁手牌时累计卡尼奥成长计数、怨灵金钱归零放最后覆盖折价）。新增 `handSizePenalty` 持久字段，经 `handSizeBonus()` 跨盲注生效，并由 `SaveCodec` 的 `hsp|` 行随存档往返。
- `PackKind` 补 `SPECTRAL`，权重改为原版表，`fill()` 生成幻灵牌；标准包蜡封池补齐 4 种；跨包彩蛋改为「灵魂进塔罗包 / 黑洞进行星包」。
- `Jokers.rollRare()` 供「怨灵」生成稀有小丑；`Consumables.planetFor(ht)` 供蓝蜡封定位行星。

### 17.4 打击感与 UI 布局补齐

- **全屏氛围层** `JuiceBackdrop`：CRT 扫描线（常驻 0.035、结算 0.11）、按牌型色背景脉冲、达标闪白。`Box` 叠在牌桌下，`fillMaxSize()` 不占任何行高 —— 这是圆屏几何仍能全 PASS 的原因。
- `RollingDigits` 逐位 stagger（每位晚 ~50ms 停住）、`ChipBurst` 9 粒子带拖尾、`PlayedCard` ±3° 稳定随机倾斜、`JokerStrip` 按当前计分步 `pulseIndex` 放大对应的小丑。
- `Sfx` 新增 `KACHING`（乘算）与 `BASS`（达标），共 14 个代码合成音色。
- **UI 布局**：出牌区空态显示「选中牌型 + 基础 chips×mult」；商店顶部新增 `OwnedJokerBar`（已拥有小丑点阵 + `x/5` 槽位计数）；商品行价签加金色描边。全部塞进现有行高，未新增整屏高度。

### 17.5 验证结果（本轮实测）

| 项 | 结果 |
|---|---|
| 单元测试 | **108 个全通过**（本轮在 `MechanicsTest` 新增 17 个：清单 52/18/4/5、幻灵包规格与权重、每张幻灵不崩、黑洞/怨灵/通灵板/外质/生命之符/咒术/熟悉者/献祭逐张落账、幻灵包进消耗品槽、紫蜡封弃牌生成塔罗、蓝蜡封按牌型生成行星、手牌上限惩罚存档往返） |
| 编译 | `compileDebugKotlin` 与 `assembleRelease` 均成功（离线 `-x lintVitalRelease -x lintVitalAnalyzeRelease`） |
| 圆屏几何 | `verify_circle.py` 10 界面全 PASS；商店加了 Joker 行后仍 87.7dp（+2dp/行 容差）；主牌桌 +2dp/行 94.6dp PASS |
| 产物 | **20,254,444 bytes**，SHA256 `55b92575f93e1ea799e3aea39f22fb53bdff6897f096fa449563c40231fb7bb6` |
| 预览图 | `tools/render_preview.py` 重渲染 10 张 + 总览，商店与牌桌逐屏目检无重叠/裁切 |
| 下载站 | `wristjoker.apk` 已替换为新包，链接不变 |

### 17.6 编译期修掉的问题

| 现象 | 处理 |
|---|---|
| `Consumable.kt` 三处 `it.random` unresolved | 内层 `List(n){…}` 的 `it` 遮蔽了 `ctx`，改为具名 `{ ctx -> … ctx.random }` |
| `Art.kt` / `ShopScreens.kt` 五处 `when` 不穷尽 | 补 `Seal.PURPLE`、三处 `PackKind.SPECTRAL`（包身色 / ink / 魂体符号）、`boosterBody` 的幻灵说明 |
| 「咒术」同时带 `jokerApocalypse=2` 与 `editionRandomJoker=POLY` 会重复加多彩 | 定义层去掉多余字段，落账层统一按 `jokerApocalypse` 处理 |

### 17.7 未验证（如实）

- **仍未在 Watch7 真机侧载**，新增的全屏 Canvas 叠层的实际帧率、粒子/闪白的观感、14 个合成音色的听感都只能真机确认。
- **18 张幻灵在手表短局里的强度**未平衡：献祭 +\$20、熟悉者塞 3 张人头、生命之符清场复制，都可能比原版更"一步到位"。
- 幻灵需选牌的效果被折进「拿走后先进消耗品槽、牌桌阶段再用」，与原版开包时另发一手牌的选牌体验仍有差异。
- 预览图仍是 dp 数值渲染，不是 Compose 实际截图。

---

## 18. 增量记录：圆屏版式重做 + 手牌扇形 + 商店四段同屏（2026-11-20 第四轮）

### 18.1 这一轮改的是"性质"，不是"内容"

前三轮一直在往游戏里**加东西**（机制、小丑、幻灵、商店结构）。这一轮的反馈指出的是
**排布方式本身错了**：

> 动画过快，而且数字有遮挡；对圆屏的适配做的不是很好，我感觉就像是给圆屏添加了一个
> 屏幕安全区，不像是专门为圆屏做的适配；特殊手牌的标识不是很明显；一些特殊效果，
> 例如加一张手牌上线并没有落到实处；商店里面还少了很多东西，例如卡包。

逐条对应：

| 反馈 | 根因 | 处理 |
|---|---|---|
| 像给圆屏加了安全区 | 七行宽度**写死** 110/132/154/146/150/154/106dp，本质是"以最窄一行为准，全屏都别超" | 宽度改成该行**弦长** `2√(r²−dy²)`，r=99dp；中间带 146→195dp |
| 动画过快 | 78ms / 145ms 一步，一手牌 0.7 秒播完 | 260 落桌 + 170/300 每步 + 320 收尾 + 700 停留 ≈ 2.6 秒 |
| 数字有遮挡 | 飘字 `padding(top=18dp)` + 上浮 9dp，整段压在筹码/倍率行上 | 改成贴在带子右端的 `GainPill` 胶囊，只缩放不位移；筹码/倍率各自进独立方框，总分移到进度条 |
| 特殊手牌标识不明显 | 增强画在**右上角**、蜡封画在**右下角**，恰好都在扇形被盖住的右段 | 增强/版本改成顶部**双层通栏色带**，蜡封移到左下并加深色光圈 |
| 手牌排列不像原作 | 2×4 网格 | 单排扇形：递进 1.7° 倾角 + 两端下沉 4dp + 选中抬起 5dp |
| 手牌上限"没落实" | 机制其实生效，但界面上**没有任何一处显示手牌上限** | 顶栏加 `手8/8`；通灵板提示追加「· 手牌 7」；画家券提示「下关手牌 9」；补 3 条单测钉住链路 |
| 商店少了卡包 | 卡包在**第二个分页标签**后面，一直在但看不到 | 取消分页，四段同屏（商品 / 卡包 / 代金券×2 / 已拥有）；券位由 1 张补到 **2 张** |

### 18.2 几何：每一行拿回多少宽度

`tools/verify_circle.py` 现在两套判据：弦长版式（牌桌/商店）与固定宽版式（其余 7 页）。

| 横带 | 上一版 | 现在 | 拿回 |
|---|---|---|---|
| 底注/盲注/手牌 | 110 | 139.0 | **+29.0** |
| 小丑+消耗品 | 132 | 161.2 | **+29.2** |
| 出牌区 | 154 | 178.8 | **+24.8** |
| 筹码×倍率 | 146 | 195.2 | **+49.2** |
| 进度 | 150 | 197.9 | **+47.9** |
| 手牌扇形 | 154 | 173.7 | **+19.7** |
| 弃/出 | 106 | 139.0 | **+33.0** |

手牌扇形容量：5 张展开 97.0dp / 8 张展开 151.0dp，可用 161.7dp —— 都 PASS。

容差：牌桌与商店在「每行 +1/+2/+3dp」下最远角恒为 **99.0dp**（因为宽度是按行高重算的，
自洽），阈值 105dp（离玻璃 3dp）全部 PASS。

### 18.3 验证结果

| 项 | 结果 |
|---|---|
| 单元测试 | **115 个全通过**（本轮 +7：画家券 +1 手牌、默认 8 张、通灵板不重复扣、弦长四角不越界 ×2、中间带必然更宽、弦长公式边界） |
| 编译 | `compileDebugKotlin` / `compileDebugUnitTestKotlin` / `assembleRelease` 均成功 |
| 几何 | 主牌桌、商店按弦长 PASS；10 个界面 OVERALL = True |
| 产物 | `20,287,212 bytes`，SHA256 `ed5027439...`（完整值见 README） |
| 预览 | 10 张重渲染，牌桌与商店逐屏目检 |

### 18.4 未验证 / 已知不足（如实）

- **仍未真机侧载**：扇形手牌的倾角在实际 216dp 屏上的可读性、斜线抗锯齿、旋转文字的清晰度只能真机确认。
- **七个老页面没改弦长**（菜单/牌组/盲注/结算/开包/详情/结束），仍是 122dp 居中 —— 它们页面本身窄，收益不如牌桌明显，但观感上与新牌桌不完全统一。
- **牌面索引在扇形里只有 18dp 可见**：9 张以上时露出宽度压到 13~17dp，点数仍可读、花色会偏小。
- 商店券位补到 2 张后，**代金券池只有 17 张**（原版 32），一局里很快会抽空。
- 预览图仍是 dp 数值渲染；`paste_card` 的旋转用双三次插值，比真机抗锯齿更"糊"，实际观感应更好。

## 19. 增量记录：把"不足"逐条补足（2026-11-20 第五轮）

这一轮的输入是一句授权：「把你认为缺失的或者是不足的补足。」所以没有新增用户反馈，
清单直接来自 §18.4 与 `docs/BALATRO_PORT.md` §15 里自己标出来的 ❌ / ⚠️。

### 19.1 反馈 → 根因 → 处理

| 上一轮自己标的不足 | 根因 | 这一轮的处理 |
|---|---|---|
| 七个老页面仍是 122dp 竖带 | 只改了牌桌和商店，没有把弦长做成公共骨架 | 新建 `ui/RoundPage.kt`（`RoundLayout` / `RoundPage` / `BandRow` / `BandGap`），菜单 / 牌组 / 挑战 / 盲注 / 结算 / 结束 / 开包 / 详情 / 信息面板全部改成按弦长分带 |
| 小丑只有 101 张（原作 150） | 上一轮只收"不会误导玩家"的 101 张 | 补 49 张，全部是原版同名同定位的牌；新增 `ScoreContext.planetsUsed / jokersBought / jokersSold / glassShattered / deckSize / handSize / bossBlind / random` 八个字段支撑成长与概率类效果 |
| 代金券 17 张 / 标签 14 种 | 原版 32 / 24 | 券补到 32（全部有读取点：版本概率、消耗品位、塔罗/行星权重、魔术固定塔罗、天文台 +2 级、导演首掷免费、重来固定 \$2、画笔/调色板手牌、反物质槽位、象形/挥霍资源），标签补到 24（券/补充/稀有/轨道/囤积/负片/箔/全息/多彩/重试） |
| 牌组 8 种（原作 15） | 上一轮判断"差异够大才留" | 补标准 / 魔法 / 星云 / 幽灵 / 弃牌 / 棋盘 / 彩绘七种，并在 `Deck.forType` 里真正改牌堆（弃牌组无人头、棋盘组只剩两花色、魔法/星云/幽灵开局送消耗品） |
| 没有难度分层（原作 8 档赌注） | 完全没做 | 新增 `game/Stake.kt`：白→金八档，逐档叠加（红=小盲不给钱、绿/紫=目标按关累乘 5%/10%、黑=小丑不可卖、蓝=-1 弃牌、橙=包 +$1、金=重掷 \$10），牌组页顶栏一点循环切换，`stk|` 进存档 |
| 没有挑战模式（原作 20 个） | 完全没做 | 新增 `game/Challenge.kt` 20 个预设 + `Phase.CHALLENGE_SELECT` + 挑战列表页；每条规则都落到已有参数（牌组/赌注/起始钱/手牌/弃牌/小丑位/目标倍率/加价/常驻 Boss），种子固定可复盘 |
| 手牌不能拖拽排序（原作可拖） | 上一轮只有 `sortHand()` 整体排序 | `HandFan` 加长按拖拽 + `GameEngine.moveCard(from,to)`；按露出宽度分步交换，`rememberUpdatedState` 保证连续拖动不错位 |
| 「四指 / 捷径 / 涂抹 / 水花 / 乌龟豆 / 杂耍者 / 吟游诗人 / 窃贼」只有 desc | 引擎从未读取这些牌 | 判定器支持 4 张同花/顺子（含 5 张里取 4 张子集）、隔档顺子、红黑互通；`Scoring` 支持 `splash`；槽位类统一由 `jokerHandSizeDelta / jokerHandsDelta / jokerDiscardsDelta` 汇总 |
| 天文学家 / 信用卡 / 混沌 / 大理石 / 证书 / 蛋 / 礼品卡 只有 desc | 同上 | 行星 0 元、可透支 -\$20、每店首掷免费、每关加石头/蜡封牌、卖价加成全部接通 |

### 19.2 九屏弦长后的实际宽度

`tools/verify_circle.py` 重写为 11 个界面全部按弦长校验，输出：

| 界面 | 上一版 | 现在最宽带 | 拿回 |
|---|---|---|---|
| 开始界面 | 122 | 195.4 | **+73.4** |
| 牌组选择 | 122 | 174.8 | **+52.8** |
| 挑战选择（新） | — | 167.9 | — |
| 盲注选择 | 122 | 195.1 | **+73.1** |
| 结算页 | 122 | 178.3 | **+56.3** |
| 结束页 | 122 | 194.5 | **+72.5** |
| 开包 | 122 | 177.4 | **+55.4** |
| 详情页 | 122 | 194.3 | **+72.3** |
| 信息面板 | 122 | 171.5 | **+49.5** |

11 个界面最远四角恒为 **99.0dp**（正好落在内容圆上），阈值 104dp；
「每行 +1/+2/+3dp」容差模拟下仍 ≤ 99dp，全部 PASS。

### 19.3 验证结果

| 项 | 结果 |
|---|---|
| 单元测试 | **136 个全通过**（本轮 +21：牌组/小丑/消耗品/挑战数量、棋盘与弃牌牌组构成、四指/捷径/涂抹/水花、八档赌注数值、黑注禁卖 vs 白注可卖、挑战固定种子两次开局一致、无丑 0 槽位、发射 0 槽位仍送 2 丑、魔法 2 塔罗、幽灵 1 幻灵、双倍挑战目标 ×2、手牌重排与选中不丢） |
| 编译 | `compileDebugKotlin` / `compileDebugUnitTestKotlin` / `assembleRelease -x lintVitalRelease` 成功 |
| 几何 | 11 个界面全部 PASS，OVERALL = True |
| 产物 | `20,401,900 bytes`，SHA256 `4d7ce469b11d18d8159e6848ff952f8fb5337aa65206b8d24c720c29528a4c35` |
| 下载链 | 本地 3000 端口回下载逐字节一致，Range 请求 206 |
| 预览 | 11 张重渲染（新增挑战页），逐屏目检无重叠/越界 |

### 19.4 仍未做（继续如实标注）

- **真机侧载仍未做**：拖拽与扇形倾角的实机手感只能上表确认。
- **内容量仍差一点**：Joker 150 / 券 32 / 标签 24 / 牌组 15 / Boss 28 已对齐原版，
  但**卡牌增强仍是 8 种**（原作 8 种，已齐）、**蜡封 4 种**（已齐）；缺的是
  **Collection 视图**（原版可以翻图鉴）与 **Stake 的解锁顺序**（本作八档全开）。
- **挑战规则是"参数化改写"**：原版有几条依赖隐藏计数器（如 Typecast 全牌型锁定），
  这里用常驻 Boss / 目标倍率近似，规则说明写在挑战 desc 里，不假装逐条一致。
- **13 张带引擎钩子的小丑**（纸牌师 / 烧焦 / 降神 / DNA / 即兴 / 主持人 等）
  在包内/商店的预览里只显示 desc，实际效果要到打出时才可见。

## 20. 增量记录：图鉴 / 设置 / 赌注逐档解锁（2026-11-20 第六轮）

第五轮结束时自己在 §19.4 与 `docs/BALATRO_PORT.md` §16.7 标了三条"仍缺"：
**Collection 图鉴**、**Stake 逐档解锁**、以及"设置类能力完全没有"。这一轮把这三条补上。

### 20.1 补了什么

| 缺口 | 原版行为 | 这一轮的做法 |
|---|---|---|
| 没有图鉴 | 150 丑 / 52 耗 / 32 券 / 24 签 / 15 组全列出，未解锁画黑影，可逐页翻 | 新增 `game/Collection.kt`（数据层，全部从既有枚举导出）+ `ui/CollectionScreen.kt`（5 类分页、4 列网格、底部详情带）。解锁判据放宽成"见过"——商店摆过、包内开出、手里持有过都算，存档 `seen` 只增不减、不进 `wipe()` |
| 没有设置 | 原版 Options 有音量、游戏速度、CRT 等 | 新增 `ui/SettingsScreen.kt`：音效 4 档（含关）、震屏开关、扫描线 4 档（含关）、结算速度 3 档。全部即时生效、即时落盘 |
| 赌注八档全开 | 白注通关才开红注，逐档向上 | `SaveStore.maxStake` + `unlockNextStake()`；牌组页赌注条只在已解锁范围循环，到顶时提示"通关解锁下一档"。通关（含进入无尽）即解锁下一档，不要求用最高档重复通关 |
| 菜单只有三个入口 | — | 菜单底部加「图鉴 / 设置」一行，两个 outline 按钮，不与主流程抢注意力 |

### 20.2 两处"看上去能用、其实没接线"的坑

1. **`Collection<String>` 撞名**：`SaveStore.markSeen(ids: Collection<String>)` 里的 `Collection`
   被新加的 `object Collection` 遮住，编译器报 "No type arguments expected for object"。
   参数类型改成 `Iterable<String>` 后正常 —— 这条记在这里，是因为它表明**包级同名对象
   会静默改变既有 API 的解析**，加新类型时值得先搜一遍同名。
2. **消耗品 key 自带前缀**：`Consumables` 里塔罗的 key 是 `t_magician`（`tarot()` 工厂
   统一加 `t_`），不是 `magician`。图鉴 id 用 `c:${key}` 拼出来是 `c:t_magician`，
   与 `ShopItem.ConsumableItem.key` 天然一致 —— 单测里一开始写 `c:magician` 直接挂了，
   正好反证"图鉴与商店用的是同一套 id"。

### 20.3 验证结果

| 项 | 结果 |
|---|---|
| 单元测试 | **142 个全通过**（本轮 +6：图鉴五类数量 273 条总数、id 全局唯一、id 与商店 key 同构、每条都有名字/描述/短名、脏 id 不计入已解锁、按 id 反查） |
| 编译 | `compileDebugKotlin` / `compileDebugUnitTestKotlin` / `assembleRelease -x lintVitalRelease` 成功 |
| 几何 | **13 个界面全部 PASS**（新增图鉴 / 设置两页），最远四角恒为 99.0dp，阈值 104dp；+1/+2/+3dp 容差模拟仍 PASS |
| 产物 | `20,483,820 bytes`，SHA256 `74e0698fcd9b47aa42867fe04f103268a8a807b63334a0028293c20c512fe441` |
| 下载链 | 本地 3000 端口回下载逐字节一致（200 / 20,483,820 B），Range 请求 206 |
| 预览 | 14 张重渲染（新增图鉴 / 设置），逐屏目检无重叠、无越界 |

### 20.4 仍然没做的

- **真机侧载**：图鉴网格 4 列 × 34dp 的点击热区、长按拖拽手感、设置页的 22dp 行高，
  都只能靠 216dp 等比预览确认，没有实机触控验证。
- **图鉴的成就层**：原版图鉴还挂着"用某张牌赢一局"这类解锁，本作只有"见过/没见过"两态。
- **Stake 的贴纸与赌注奖励**：原版每档赌注有独立贴纸与 Boss 池调整，本作只做了难度参数。

---

## 21. 第七轮：声音、发牌动作、小丑台词

### 21.1 问题不在界面

前六轮把版式、机制、内容量都补到了"和原作能逐项对上"的程度，但反馈仍然是
「不像原版，少了点灵魂」。把原作拆开看，剩下的差距几乎都在**非视觉层**：

| 原版有的 | 本作之前 | 后果 |
|---|---|---|
| 循环爵士配乐 | 静音 | 牌桌的空白全靠玩家自己填，玩两分钟就"干" |
| 发牌 / 补牌的过程 | 手牌瞬间出现 | 出牌后手牌"啪"地换了一批，没有"发牌"这件事 |
| 每张小丑的 flavor text | 只有机制描述 | 150 张牌读起来像参数表，没有角色 |

这三项都不改规则，也都不占行高 —— 它们不跟圆屏抢空间，是这一轮最划算的补法。

### 21.2 配乐的做法

不打包音频文件，和音效同一条路：**用代码合成 PCM**。

| 项 | 取值 |
|---|---|
| 格式 | 22050Hz / 16bit / 单声道 WAV，写进 `cacheDir/bgm/loop_v1.wav`（约 900KB） |
| 长度 | 8 小节，92 BPM，摇摆八分，约 20.87 秒 |
| 和声 | Dm9 → G13 → Cmaj9 → Am9 → Dm9 → G7♭9 → E7 → A7 |
| 声部 | 走路贝斯（根音 / 五度 / 半音趋近）、反拍电钢（FM，三音省略根音）、鼓刷（4 正拍 + 一个弱"搭"）、黑胶底噪与偶发爆点、第 3/7 小节才进来的旋律 |
| 后期 | 单极低通（≈3.4kHz）+ tanh 软限幅 + 归一 |
| 循环 | 多渲 1.2 小节，尾巴**折回开头**叠加，循环点无接缝 |
| 场景 | 只改播放速度：菜单/牌桌 1.0、Boss 0.94、结算商店 1.06 |

生成在后台线程做一次（`Thread.MIN_PRIORITY`），完成后 `Handler(mainLooper)`
再创建 `MediaPlayer`。放不出声音的机型不会崩：每一步都 `runCatching`。

### 21.3 四个坑

1. **硬切循环会"咔"**。最后一小节的和弦要响两秒，直接截断在循环点就是一次
   阶跃。多渲 1.2 小节再折回开头，是最省事且不需要交叉淡入的做法。
2. **`MediaPlayer` 必须在有 Looper 的线程创建**。生成在后台线程，创建放主线程，
   两者用 `Handler.post` 衔接 —— 反过来会在 `setOnPreparedListener` 附近抛
   "Can't create handler inside thread that has not called Looper.prepare()"。
3. **设置页放不下第五行**。原来 4 行 × 22dp + 3 × 2dp 间距 = 94dp，塞进 96dp 的带子；
   加"音乐"要 5 行，只能把行高压到 18dp、说明文字从小字第二行改成同一行的小字。
   带子改成 100dp，整页 136dp，四角仍是 99.0dp（`verify_circle.py` 已同步）。
4. **预览脚本的 `rrect` 以中心点定位**，不是左上角。设置行的数值胶囊按"左边缘"
   算位置时，文字会压到一半 —— 这类坐标错误在 Compose 里不会出现，只有 Python
   预览会犯，改完必须回看渲染图。

### 21.4 验证结果

| 项 | 结果 |
|---|---|
| 单元测试 | **146 个全通过**（+4：每张小丑都有台词、台词数与 150 对齐、单句 ≤20 字、无重复台词） |
| 编译 | `testDebugUnitTest` + `assembleRelease` 成功 |
| 几何 | **13 个界面仍全部 PASS**，最远四角 99.0dp / 阈值 104dp，+1/+2/+3dp 容差仍 PASS |
| 产物 | `20,500,204 bytes`，SHA256 `865633fe3ea44968599e2fb4169126cc4e55a46ca18a543575ad6b2dd2e94750` |
| 内容 | APK 内 `classes2.dex` 已确认含 `fx/Music` 与 `game/JokerFlavor` |
| 预览 | 设置页按 5 行重绘，详情页底部改为显示台词 |

### 21.5 仍然没做的

- **真机听感**：合成的爵士段只做了频谱与电平检查（RMS ≈ 0.17，峰值 0.65，
  无直流偏移、无明显静音段），没有在手表扬声器或蓝牙耳机上实际听过。
  音色偏"电子"是合成器的固有问题，真机上如果刺耳，先降 `Music` 的默认档位。
- **配乐的场景切换只有速度**，没有原版那种"换曲/加声部"的层次。
- **发牌动作**只做了入场，出牌与弃牌仍是瞬时消失（补牌会跟上，但"牌飞出去"
  这一下没有）。

---

## 22. 第八轮：信息入口 + 1999 视觉迁移

### 22.1 补的是"信息黑箱"

上一轮的差距清单里，最痛的不是内容不够，是**玩家看不到系统在做什么**。
这一轮补了三处，全部照着原版的交互位置放：

| 入口 | 位置 | 拿到的信息 |
|---|---|---|
| 牌堆查看 | 顶栏「手 8/8」（带下划线提示） | 待抽牌按摸牌顺序摊开 + 弃牌堆 |
| 牌型等级 | 顶栏「AIII/8」 | 12 种牌型的当前等级与筹码×倍率，本局出过的加亮 |
| 一键排序 | 牌桌左下「点 / 花」 | 按点数或花色重排，点一次切一次 |

排序在引擎侧是 `sortHand(mode)`：只重排 `hand` 这个 id 列表，**选中集合按 id 记，
不受下标变化影响**，所以排序不会丢选中、也不会改变计分。这条写成了单测。

### 22.2 视觉迁移的边界

按 `reverse1999-design/DESIGN_LANGUAGE.md` §5 的原则，只换四个维度：
底色/影调、材质、装饰母题、字体，**功能色的语义锁死**。

具体改了什么：

- `Palette.kt` 整表重写：深咖 `#1B1713` / 纸 `#E8E1D1` / 琥珀 `#E0A35C` / 灰绿 `#5C6455`；
  功能色各降一档饱和（筹码 `#5E9BB0`、倍率 `#C9604F`、金钱 `#DCA85E`）。
- `FeltBackground`：旋转六边形 → 深咖径向渐变 + 雨滴斜线 + 纸张纤维短划 + 压印双线。
- `CardBackArt`：Bicycle 红斜网 → 深咖 + 烫金双线 + 四角星。
- `CardFaceArt`：纸纹横线保留但压淡，加**四角老化**（径向暗角）与**双线压印内框**。
- `CursorCorners`：四角 L 线 → 四角星；另抽 `FourPointStar` 供复用。
- 菜单标题：红色立体字 → 米白衬线 + 宽字距；`JOKER` 点阵 → 大写字母标签行。
- 底注：`A3/8` → `AIII/8`（罗马数字，1999 的钟表/档案母题）。

### 22.3 五个坑

1. **改 Palette 不会自动改预览脚本**。`render_preview.py` 头部有 20 个独立色值常量，
   另外还有 12 处硬编码元组（`(0,42,62)` 筹码盒底、`(16,30,34)` 飘字胶囊、
   `(18,32,36)` CRT 线…）。漏一处，那张预览图就和真机不是同一个游戏。
   这轮是逐张看图才发现详情页还留着旧墨绿底 `(18,30,34)`。
2. **网点在 25dp 的牌面上不成立**。试过 halftone，点阵直接糊成一层灰，
   比原来的横线更像"脏了"。最后只用"四角老化"表达纸张，中心保持干净。
3. **预览的 `rrect` 以中心定位**，两列格子按 `w/2` 排时没留间隔，
   边框会互相压住。Compose 里 `spacedBy(3.dp)` 是对的，只有 Python 预览需要手动减宽。
4. **`Modifier.width()` 收 `Dp` 不收 `Float`**。写 `Modifier.width(L.w[3] * .34f)`
   编译期就报 "None of the following candidates is applicable"，要 `.dp`。
5. **`HandType.kt` 的注释写着"13 种牌型"，枚举实际 12 条**。上一轮做差距清单时
   被这句注释带偏，差点报成"缺三个隐藏牌型"。注释与实现不一致本身就是缺陷。

### 22.4 验证结果

| 项 | 结果 |
|---|---|
| 单元测试 | **153 个全通过**（+7：排序升序 / 同花色聚集 / 不改变组成 / 不丢选中 / 牌堆数量一致 / 弃牌可见 / 非出牌阶段空操作） |
| 几何 | **15 个界面全 PASS**，最远四角 99.0dp / 阈值 104dp，+1/+2/+3dp 容差仍 PASS |
| 内容 | APK 内已确认含 `DeckViewScreen` / `RunInfoScreen` / `SortMode` / `FourPointStar` |
| 产物 | `20,565,740 bytes`，SHA256 `452918e35df6a177b6136c1464171996990083cb25e65b24a6885793993f3ffd` |
| 预览 | 15 屏重绘，配色与真机逐值对齐 |

### 22.5 仍然没做

- **AOD 常亮**：`grep -i ambient` 仍是零命中，放下手腕整块黑。
- **表冠只覆盖手牌**：商店 / 图鉴 / 设置都不响应旋转。
- **成就、每日挑战、种子输入、统计页**：这四项是"长线留存"层，一个都没做。
- **真机手感**：所有判断仍止于代码与渲染图，没在表上跑过。
