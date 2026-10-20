# Pie Launcher

[![Android 8-16](https://img.shields.io/badge/Android-8--16-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Release](https://img.shields.io/badge/Release-latest-brightgreen?logo=github&logoColor=white)](https://github.com/kj2431/pie-launcher/releases/tag/Releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Donate PayPal](https://img.shields.io/badge/Donate-PayPal-00457C?logo=paypal&logoColor=white)](https://www.paypal.com/donate/?business=HC9A86GXJZ58S&no_recurring=0&currency_code=USD)
[![Donate ETH](https://img.shields.io/badge/Donate-ETH-627EEA?logo=ethereum&logoColor=white)](https://etherscan.io/address/0xea115154408b1a3939678adf5a5ab5b8b15dcb11)
[![Donate BTC](https://img.shields.io/badge/Donate-BTC-F7931A?logo=bitcoin&logoColor=white)](https://www.blockchain.com/btc/address/14DremGQ8NiVjrxXBAfqghdrjSWaiFXB9h)

An original, optimized, and root-capable radial **PIE gesture launcher** for Android.
Swipe in from a screen edge to open a half-donut pie menu, drag to a slice, and lift to
fire an app or a system/root action. Portions of this code feature vibe coding from popular
LLMs.

> Package: `com.kj2431.pielauncher` · minSdk 26 · targetSdk 35 · Kotlin

<p align="left">
  <img src="screenshot.jpg" width="300" alt="Pie Launcher screenshot">
</p>

## Inspiration

This project deeply was inspired by **LMT Launcher** by noname81
([XDA thread](https://xdaforums.com/t/app-root-lmt-launcher-v3-2.1330150/)),
a pioneering PIE/gesture control APP for rooted Android devices. Originally released
over 10 years ago, as a heavy user of LMT Launcher, I have realized that it has not
been updated for modern Android releases, and there have been bugs over the years.

Pie Launcher modernizes the concept for Android 15 and 16 — with full edge-to-edge
display support, Material You dynamic color, Material 3 Expressive icons, the
modern overlay and foreground-service APIs, and a persistent root shell that works
reliably with current superuser managers like Magisk. It is an independent
reimplementation built from scratch; no code or assets from LMT are included or
redistributed.

## Features

- **Edge activation strips** on any combination of Left / Right / Top / Bottom.
- **Center-point placement**: choose where along the edge the activation area is
  centered, and a separate length to grow/shrink it.
- **Half-donut PIE** that fans inward from the edge, opening in place with a
  time-based ease-out animation. Slice ordering is consistent per edge.
- **Short- and long-press per slice**: a long-press swaps the slice face to the
  long action's own icon/label before it fires.
- **~30 actions** (Home, Back, Recents, Notifications, Quick Settings, screen
  lock, Wi-Fi / Bluetooth / data toggles, screenshot, split-screen, launch app,
  open URL, run shell script, and more) via Accessibility and/or root.
- **Material 3 Expressive icons** — authentic Material Symbols (Rounded, filled).
- **Three configurable PIE colors**: resting background, click highlight, and
  long-press highlight. Defaults are derived from the device's Material You
  palette (the darkest of three system colors becomes the resting background).
- **Theme**: System / Light / Dark / AMOLED black, with status- and navigation-
  bar icon contrast handled automatically.
- **Single persistent root shell** — root is requested once at activation and
  reused, so the superuser manager doesn't prompt on every command.
- Optional **AccessibilityService** for non-root global actions, and a boot
  receiver to restart the service after reboot.
- **Language**: follow the system, or pick 简体中文 / English per app (also
  available in Android 13+ system app-language settings).
- **Backup & restore**: export all settings and slices to a JSON file and
  import them back (on the same or another device).
- **Hide in landscape**: optionally remove the activation strips while the
  screen is landscape (games, videos).

## Changelog（本 fork 的修改记录）

### 2026-10-20

**显示与适配**
- 触发条长度改为按竖屏时的边长计算：顶部/底部用屏幕短边，左右用长边。横屏时底部触发条不再变宽。
- 触发条和展开后的扇形窗口允许覆盖摄像头挖孔区域，修复横屏时左侧触发条不贴边（被挖孔推开 136px）的问题。
- 屏幕旋转或语言变化时自动重建触发条；如果扇形正打开着，会等手势结束后再重建。
- 在边缘两端按下时，圆心自动向内移动，保证整个扇形都在屏幕内。
- 新增「横屏时隐藏」开关。

**稳定性与流畅度**
- root 命令超过 15 秒（root 授权弹窗 30 秒）会结束当前 shell 并在下次自动重开，不会再因为一个卡住的脚本导致所有动作失效。
- 无障碍动作和普通动作直接执行，不再和 root 命令排队。
- 切片的名称和图标在创建触发条时就准备好，应用安装、更新或卸载时自动刷新，按下时不再查询 PackageManager。
- 圆环信息（电量、CPU、内存、网络、通知）改到后台线程收集，时钟仍然立即显示。
- 绘制时复用 `Path` / `RectF`，动画期间不再每帧新建对象。

**功能**
- 「最近应用 / 上一个应用 / 下一个应用」：有 root 时读取最近任务列表，直接切换到目标应用（跳过桌面、当前应用和其他用户的应用分身）；没有 root 时退回到“连按两次多任务键”。「下一个应用」会切到最久没用的应用，连续使用可以依次轮换。
- 简体中文翻译；所有写死在代码里的文字都改为字符串资源。
- 设置中可指定语言：跟随系统 / 简体中文 / English。
- 配置导入导出（设置 →「备份与恢复」），使用系统文件选择器，不需要存储权限。不会导出“服务是否启动”，导入前会先确认。

**构建**
- release 构建开启 R8 代码和资源压缩（APK 约 6.1 MB → 1.9 MB）。崩溃堆栈需要用 `app/build/outputs/mapping/release/mapping.txt` 还原。
- 构建需要 JDK 17–21（Gradle 8.9 不支持 JDK 25）。
- 用 debug 密钥签名 release 版，可以覆盖安装 debug 版而不丢数据：

  ```bash
  ./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore \
    -Pandroid.injected.signing.store.password=android \
    -Pandroid.injected.signing.key.alias=androiddebugkey \
    -Pandroid.injected.signing.key.password=android
  ```

## Build

Requires JDK 17–21 (Gradle 8.9 rejects JDK 25) and the Android SDK (compile SDK 35).

```bash
# Debug
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release (signed - see "Release signing" below)
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Point the build at your SDK by either setting `ANDROID_HOME` / `ANDROID_SDK_ROOT`
or creating `local.properties` with `sdk.dir=/path/to/Android/sdk`.
(`local.properties` is git-ignored.)

## Release signing

Signing credentials are read from a **git-ignored** `keystore.properties` at the
repo root, so no secrets live in version control. To set it up:

1. Create a keystore (once) and **back it up safely** — losing it means you can
   no longer ship updates under the same app identity:

   ```bash
   keytool -genkeypair -v \
     -keystore pielauncher-release.keystore \
     -alias pielauncher -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` next to `settings.gradle.kts`:

   ```properties
   storeFile=pielauncher-release.keystore
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=pielauncher
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. Build: `./gradlew assembleRelease`. If `keystore.properties` is absent, the
   release build runs without the release signing config (debug builds are
   unaffected).

Both `keystore.properties` and `*.keystore` / `*.jks` are listed in `.gitignore`.

## Permissions

`SYSTEM_ALERT_WINDOW` (the overlay), `FOREGROUND_SERVICE` (+ special use),
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, and `VIBRATE`. Root actions use
`su` only through the isolated `RootHelper`; the AccessibilityService performs
global actions only and reads no screen content.

## Project layout

```
app/src/main/java/com/kj2431/pielauncher/
  service/   overlay service, accessibility service, boot receiver
  view/      PieEdgeView (donut renderer + gesture handling)
  action/    action catalog + runner (none / accessibility / root)
  root/      RootHelper (single persistent su shell)
  model/     PieItem, AppInfo
  prefs/     Prefs (SharedPreferences source of truth), ConfigBackup (JSON import/export)
  info/      RingInfo (clock + status lines around the open pie)
  ui/        edge-to-edge, theme and language helpers
  *Activity  control surface, settings, PIE/app/command pickers
```

## Support / donate

If this is useful to you and you'd like to support development:

**[Donate via PayPal](https://www.paypal.com/donate/?business=HC9A86GXJZ58S&no_recurring=0&currency_code=USD)**

Crypto:

- **ETH:** `0xea115154408b1a3939678adf5a5ab5b8b15dcb11`
- **BTC:** `14DremGQ8NiVjrxXBAfqghdrjSWaiFXB9h`

## License

Copyright 2026 kj2431

Licensed under the **Apache License, Version 2.0** (the "License"); you may not
use this software except in compliance with the License. You may obtain a copy
of the License at:

> <https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

See [LICENSE](LICENSE) for the full license text.

PIE menu icons are [Material Symbols](https://fonts.google.com/icons) by Google,
also licensed under the Apache License 2.0 (see [NOTICE](NOTICE)).
