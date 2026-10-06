# Fieldwatch-zh（Fieldwatch 简体中文版）

这是 [OffGridPete/Fieldwatch](https://github.com/OffGridPete/Fieldwatch) 的**非官方简体中文汉化版**。

Fieldwatch 是一个仅接收（receive-only）的 Wi-Fi 接入点 / 蓝牙低功耗（BLE）广播观察工具，用于查看周围有哪些无线设备在广播。它只监听、不连接、不上传，没有账号、没有后端服务器。

- 原始项目：<https://github.com/OffGridPete/Fieldwatch>（MIT License）
- 本仓库：`Fieldwatch-zh`
- 安装包：`dist/Fieldwatch-zh.apk`
- 应用包名：`app.fieldwatch`（与原版相同）
- 最低系统：Android 10（API 29）

> 本仓库与原作者 Off Grid Pete LLC 无关，仅为个人汉化，按 MIT 协议分发。原作者版权归 Off Grid Pete LLC 所有。

## 汉化说明

原应用的界面文字几乎全部硬编码在 Kotlin 源码里（`strings.xml` 中只有 `app_name`），因此本汉化直接替换了源码中的用户可见字符串，覆盖：

- 底部导航与各页面标题：实时 / 筛选 / 特征 / 报告 / 设置 / 详情 等
- 按钮、开关、下拉、输入框标签与占位符
- 各类对话框、提示、状态、空状态与错误信息
- 报告与简报（Debrief）中的标题与说明文字
- 设备详情页的解释性文字和数据解码字段标签

翻译对照表保存在 [`translations/zh-CN.json`](translations/zh-CN.json)（英文 -> 简体中文），便于审阅与后续维护。

专业名词（Wi-Fi、BLE、MAC、OUI、RSSI、SSID、GPS、GPX、KML、CSV、JSON、UUID、dBm 等）以及品牌/产品专名（如 Fieldwatch、Apple、Samsung 等）保持原文。特征库（Signatures）中大量条目为厂商/产品专名，保留英文。

## 自行编译

需要 JDK 17+ 与 Android SDK（compileSdk 35）。仓库自带 Gradle Wrapper：

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

在 Termux / Android 上编译时，可让 Gradle 使用系统自带的 aapt2：

```bash
./gradlew -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2 assembleDebug
```

并创建 `local.properties`：

```properties
sdk.dir=/path/to/android-sdk
```

## 安装

`dist/Fieldwatch-zh.apk` 为侧载（sideload）安装包，使用 debug 签名。Android 可能提示“未知来源”或 Play Protect 警告，确认可信后再安装。请先打开定位、Wi-Fi、蓝牙，并在首次启动时授予相关权限。

安装前建议阅读原项目提供的说明与手册（原仓库 `dist/instruction.txt` 与 `dist/Fieldwatch_User_Manual.pdf`）。

## 免责声明

本项目为爱好性质的汉化，按“原样”提供。无线电探测受手机硬件与系统限制，无法保证发现任何设备；特征匹配与“随你移动”等提示只是推测，不构成身份认定或法律结论。使用者需自行承担一切风险并遵守当地法律。详见原项目 README 与 `LICENSE`。

## License

MIT License。原始版权归 Off Grid Pete LLC；AndroidX / Kotlin 等依赖为 Apache-2.0；IEEE 与 Bluetooth SIG 的查表数据受其各自条款约束，见 `NOTICE`。
