# AGENT.md

面向后续 AI 编码代理 / 自动化协作者的仓库说明。开始修改前先读本文件，避免破坏现有产品约束和构建配置。

## 0. 当前状态

- 应用：**CutGIF**，Android 原生视频转 GIF + GIF 编辑工具。
- 当前版本：`versionName = 1.0.0`，`versionCode = 10`，主页右上角显示 `v1.0.0`。
- Debug applicationId：`com.fgxyx.cutgif.debug`；正式包名：`com.fgxyx.cutgif`。
- 当前 APK：`app/build/outputs/apk/debug/app-debug.apk`（约 48 MB，FFmpegKit video 库占主要体积）。
- 远程仓库：`https://github.com/FGXYX/CutGIF.git`，默认分支 `main`。
- 界面语言：简体中文；README 提供中文和英文两个版本。
- 项目代码许可证：**Apache License 2.0**（`LICENSE` / `NOTICE`）；第三方许可见 `THIRD_PARTY_NOTICES.md` 和 `licenses/`。

## 1. 项目定位

CutGIF 完全在设备本地处理媒体，不上传文件，也不需要网络权限。两个核心入口：

1. **视频转 GIF**：Photo Picker 选择视频 → 裁切 / 时间区间 / 预览倍速 → FFmpeg 编码 GIF。
2. **GIF 拆分 / 编辑**：选择 GIF → 帧轴选择起止帧 → 重新编码选中区间为新的循环 GIF。

> 注意：GIF 拆分**不是**导出 PNG/JPG 图片序列。导出结果必须仍然是 `.gif`，并保留原始帧延迟。

## 2. 不可破坏的产品约束

- **Light-only Material 3**：只使用浅色主题；业务 UI 取色必须走 `MaterialTheme.colorScheme`，不要在组件里写字面量颜色。
- **单 Activity + Compose Navigation + ViewModel 状态提升**。不要引入多 Activity 或全局可变 UI 单例。
- 竖屏设计基准 **412 × 892 dp**；手机端布局必须仍可用。
- 形状和间距走 `CutGifShape` / `CutGifDimens`，不要散落新的魔法数字；8dp 栅格，44dp 微调按钮、3dp Connected Group 间距是明确例外。
- 所有用户可见字符串放在 `app/src/main/res/values/strings.xml`；不要在 Compose 里堆中文硬编码（错误提示、日志、第三方 API 字段除外）。
- 不申请网络权限；不要上传媒体到服务端。
- **视频导出主路径**是 FFmpegKit；失败时才允许回退到旧编码器，并且 UI 必须显示回退提示。
- **GIF 编辑导出**使用 `GifEncoder` + `ColorQuantizer` 逐帧重编码，输出单个 GIF；不要再生成图片序列。
- 不要悄悄改变导出分辨率、帧延迟、循环方式或保存目录；当前行为见下文。

## 3. 目录与关键文件

```
app/src/main/java/com/fgxyx/cutgif/
├── MainActivity.kt
├── data/
│   ├── VideoFrames.kt             # 视频元数据 / 缩略图 / 方向
│   ├── GifExportRepository.kt     # FFmpeg 导出、MediaStore、分享、旧编码回退
│   ├── FfmpegGifExporter.kt       # FFmpeg 两遍调色板编码、进度、AV1/libdav1d
│   ├── GifFrameCounter.kt         # GIF 帧数校验
│   ├── GifMetadata.kt             # GIF 尺寸 / 帧数 / 每帧延迟解析
│   ├── GifFrameRenderer.kt        # 按帧号渲染 GIF（Movie）
│   ├── GifSplitRepository.kt      # GIF 编辑重编码、MediaStore、分享
│   ├── ExportModels.kt            # 视频导出参数 / ColorQuality / 清晰度
│   └── CropRectN.kt
├── gif/
│   ├── GifEncoder.kt              # GIF89a / LZW / 全局调色板 / 帧延迟
│   └── ColorQuantizer.kt          # Median Cut + 抖动 + 缓存
├── ui/
│   ├── CutGifApp.kt               # NavHost：home / editor / gif_split
│   ├── home/HomeScreen.kt
│   ├── editor/                    # 视频编辑、胶片条、裁切、导出面板
│   ├── split/                     # GIF 帧轴、GIF 编辑导出面板、ViewModel
│   ├── components/                # Connected Group、按压缩放
│   ├── theme/                     # Color.kt / Shape.kt / Dimens.kt
│   └── util/
app/src/test/...                   # GifEncoder 单元测试
app/src/androidTest/...            # 首页 Compose UI 测试
```

关键状态：

- 视频编辑：`EditorViewModel`。
- GIF 编辑：`GifSplitViewModel`。
- 导航和页面转场：`CutGifApp.kt`。
- 视频导出选项：`data/ExportModels.kt` + `ui/editor/ExportSheet.kt`。
- GIF 编辑导出选项：`ui/split/GifSplitExportSheet.kt`。

## 4. 构建

要求：JDK 17+、Android SDK Platform 37、Build Tools 36、API 26+ 设备。

本机环境曾出现 `JAVA_HOME` 被污染的问题，构建前显式设置：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
```

常用命令：

```powershell
# Debug APK
.\gradlew.bat :app:assembleDebug

# 单元测试
.\gradlew.bat :app:testDebugUnitTest

# Lint
.\gradlew.bat :app:lintDebug

# 仪器化测试（先连接设备/模拟器）
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Gradle 代理

仓库中的 `gradle.properties` 不应包含某台机器专用的 `127.0.0.1` 代理。若当前网络必须走代理，请写到用户级配置（不要提交）：

```text
%USERPROFILE%\.gradle\gradle.properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897
```

## 5. 功能不变量

### 视频转 GIF

- 时间窗最长 60 秒；60 秒及以上按 10 秒分格，不足 60 秒按 6 等分格显示完整时长。
- 胶片条固定尺寸缩略图、中间裁切；拖动时缩略图 / 刻度 / 时间戳连续平移，窗口和截取区间同步移动且长度不变。
- 拖动使用胶片条本地状态，约 60fps；每 80ms 提交一次 ViewModel；松手提交最终值并 seek。
- 播放循环 `LaunchedEffect` 不要以 start/end 为 key；拖动期间不逐帧 seek。
- 截取区间 0.1–60 秒。
- 导出分辨率 144P / 240P / 360P / 自定义；FPS 5–60；FFmpeg 主路径用 `fps` 滤镜，不用逐帧 `MediaMetadataRetriever`。
- AV1 必须走 `-c:v libdav1d`；FFmpegKit 必须使用 **video** 变体。
- 导出成功写 `Pictures/CutGIF`，Q+ 走 MediaStore。

### GIF 拆分 / 编辑

- 选择 GIF 后进入帧轴工作台；胶片条每 **10 帧**一张预览，编号按 1 基显示（#1、#11、#21…）。
- 可拖拽开始/结束帧、逐帧微调、点缩略图定位、在选区循环预览。
- 导出仍然是 **单个循环 GIF**，不是图片序列。
- 保留原 GIF 每帧延迟（GIF 1/100 秒单位）；不要用固定 FPS 重采样。
- 导出选项只有：色彩品质（低/中/高/最高）和输出尺寸（原始/75%/50%/25%）。
- 透明区域当前会填充白色；这是已知限制，不要声称保留透明 GIF。
- 保存路径：`Pictures/CutGIF/CutGIF_yyyyMMdd_HHmmss.gif`。
- 成功页应能播放导出的 GIF、显示文件大小/尺寸/路径，并支持分享。

## 6. 测试与验收

最低验收：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest
```

GIF 编辑手工回归：

1. 准备 30 帧、80ms/帧的测试 GIF。
2. 选择 GIF，确认帧轴 #1/#11/#21 预览、总帧数正确。
3. 拖拽开始帧到 #4、结束帧到 #26，确认范围 23 帧。
4. 导出 GIF，确认文件可以播放、循环、23 帧、总时长 1840ms。
5. 用 `PIL`/系统相册检查输出为 animated GIF，而不是 PNG/JPG 序列。

视频导出回归：查看 `CutGifExport` 日志，确认出现 `EXPORT_START`、`ffmpeg engine=`、`EXPORT_DONE`，没有 `FFMPEG_FALLBACK`。

## 7. 上传 / 发布检查清单

提交到 GitHub 前逐项确认：

- [ ] `versionName`、`versionCode`、`strings.xml` 的 `version_label`、README（中英）版本号一致。
- [ ] `README.md` / `README.en.md` 的 AI 免责声明和语言切换入口仍在。
- [ ] 运行单元测试、Lint、Debug 构建、仪器化测试。
- [ ] 确认 APK 可安装；记录 APK 路径和大小。
- [ ] `git status` 不包含 `local.properties`、`app/build/`、`.gradle/`、`.kotlin/`、`.idea/`、测试 GIF、日志、keystore、token。
- [ ] 不提交 `*.jks` / `*.keystore` / `.env`。
- [ ] 不把机器专用代理写入 `gradle.properties`。
- [ ] 不提交 `*.apk`（构建产物由本地生成；如确需发布，放到 GitHub Releases，不要塞进源码）。
- [ ] 源码、`gradle/wrapper`、README、docs 截图、`1.jpg` 图标源文件可上传。
- [ ] `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md` 和 `licenses/` 已包含，且没有被误删。
- [ ] 若修改依赖或 FFmpegKit 变体，同步更新第三方声明和许可证文本。
- [ ] 对外分发 APK 时检查 FFmpeg/FFmpegKit 的 LGPL 源码提供、许可证保留和可替换/重链接要求。
- [ ] 推送后确认远程 `main` 的提交和文件列表。

首次上传：

```powershell
git init
git branch -M main
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/FGXYX/CutGIF.git
git push -u origin main
```

后续提交：

```powershell
git add .
git commit -m "feat: ..."
git push
```

不要对空仓库使用 `--force`，也不要在未确认远程内容前强推。

### 开源协议与第三方合规

- 应用代码：Apache License 2.0，根目录 `LICENSE`；版权声明在 `NOTICE`。
- FFmpegKit maintained：LGPL-3.0，文本在 `licenses/LGPL-3.0.txt`。
- FFmpeg：LGPL-2.1+，文本在 `licenses/LGPL-2.1.txt`。
- libdav1d：BSD-2-Clause；libvpx / smart-exception-java：BSD-3-Clause，文本在 `licenses/`。
- 不要删除或弱化 `THIRD_PARTY_NOTICES.md` 中的归属和 LGPL 分发说明。
- Apache-2.0 不会替代 FFmpeg/FFmpegKit 的 LGPL 义务；发布 APK 前必须单独核对。
- 本文件与 README 的说明不构成法律意见。

## 8. 已知限制

- `GifFrameRenderer` 使用 Android `Movie`（已废弃但 API 26+ 可用），按帧起始时间定位；不同 GIF 的帧时间可能有毫秒级偏差。
- GIF 编辑使用自定义 `GifEncoder`，大分辨率 / 长帧数导出会慢于 FFmpeg；进度按帧推进。
- GIF 编辑不支持保留透明通道，透明区域会变白。
- Debug APK 约 48 MB，主要是 FFmpegKit video 的 native 库；这是 AV1 软件解码的代价。
- 当前没有 Release 签名；对外分发前需要配置签名和 Release 构建。
- `CutGIF` 所有处理都在本地，不上传媒体，不申请网络权限。

## 9. 给代理的工作方式

1. 先读本文件、`README.md`、相关 ViewModel / Repository，再改代码。
2. 修改功能时优先复用现有 `CutGifDimens`、`CutGifShape`、`ColorQuality`、`GifSplitScale` 等模型。
3. 改导出逻辑必须同时检查：进度、取消、缓存清理、MediaStore/FileProvider、成功页提示。
4. 改 UI 后至少构建 + Lint；涉及交互时补仪器化测试或手工验收。
5. 提交前执行第 7 节清单，不要顺手提交构建产物或本机配置。

## 10. 免责声明

本仓库包含 AI 辅助生成或大幅修改的代码、文档和截图。使用、分发或商用前请自行审查、测试和验证。详见 README 中的 AI 生成内容免责声明。
