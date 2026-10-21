# Third-Party Notices / 第三方组件声明

CutGIF 的应用代码采用 [Apache License 2.0](LICENSE)。本文件列出主要第三方组件、许可证和分发注意事项。

本文件不是法律意见。完整许可证文本位于 [`licenses/`](licenses/) 和根目录 [`LICENSE`](LICENSE)；如果某个组件的上游声明与本文件不一致，以上游声明为准。

## Runtime components / 随应用分发

### AndroidX / Jetpack Compose / Media3 / Navigation / Lifecycle

- License: Apache License 2.0
- Copyright: Google LLC / The Android Open Source Project
- Components include `androidx.core`, `androidx.activity`, `androidx.lifecycle`,
  `androidx.navigation`, `androidx.compose.*`, `androidx.media3`.
- Links: <https://developer.android.com/jetpack/androidx> ·
  <https://developer.android.com/jetpack/androidx/releases/compose>

### Kotlin

- License: Apache License 2.0
- Copyright: JetBrains s.r.o. and Kotlin contributors
- Link: <https://kotlinlang.org/>

### FFmpegKit maintained

- Artifact: `dev.ffmpegkit-maintained:ffmpeg-kit-video:8.1.7`
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- Copyright: the FFmpegKit maintainers and contributors
- Link: <https://github.com/ffmpegkit-maintained/ffmpeg>
- License text: [`licenses/LGPL-3.0.txt`](licenses/LGPL-3.0.txt)

### FFmpeg

- License: GNU Lesser General Public License v2.1 or later (LGPL-2.1+),
  as configured by the FFmpegKit `video` variant. This project does not
  intentionally enable GPL-only FFmpeg components.
- Copyright: FFmpeg developers
- Links: <https://ffmpeg.org/> · <https://ffmpeg.org/legal.html>
- License text: [`licenses/LGPL-2.1.txt`](licenses/LGPL-2.1.txt)

### libdav1d

- License: BSD 2-Clause
- Copyright: VideoLAN and dav1d authors
- Link: <https://code.videolan.org/videolan/dav1d>
- License text: [`licenses/BSD-2-Clause.txt`](licenses/BSD-2-Clause.txt)

### libvpx

- License: BSD 3-Clause
- Copyright: Google Inc. / The WebM Project authors
- Link: <https://chromium.googlesource.com/webm/libvpx>
- License text: [`licenses/BSD-3-Clause.txt`](licenses/BSD-3-Clause.txt)

### smart-exception-java

- Artifact: `com.arthenica:smart-exception-java:0.2.1`
- License: BSD 3-Clause
- Copyright: Arthenica
- Link: <https://github.com/arthenica/smart-exception>
- License text: [`licenses/BSD-3-Clause.txt`](licenses/BSD-3-Clause.txt)

## Build and test only / 仅构建与测试

These components are not shipped inside the runtime APK.

- Gradle Wrapper — Apache License 2.0 — <https://gradle.org/>
- JUnit 4 — Eclipse Public License 1.0 — <https://junit.org/junit4/>
- AndroidX Test / Espresso — Apache License 2.0 — <https://developer.android.com/training/testing>

## FFmpeg / FFmpegKit LGPL compliance

CutGIF ships FFmpeg native libraries inside the APK and loads them at runtime.
For redistribution of an APK containing these libraries:

1. Keep this notice, the applicable LGPL texts, and the FFmpeg/FFmpegKit copyright notices with the distribution.
2. Provide the corresponding source code for the LGPL-covered libraries, or a valid written offer to provide it, including the build configuration used to produce the binaries.
3. Do not impose terms that prohibit replacing or relinking the LGPL libraries. The APK uses a standard Android native-library layout; users may rebuild the app with modified LGPL libraries.
4. Include the licenses of bundled codec libraries such as libdav1d and libvpx.

Upstream source and build scripts:

- FFmpegKit maintained: <https://github.com/ffmpegkit-maintained/ffmpeg>
- FFmpeg: <https://ffmpeg.org/download.html>
- FFmpeg legal / license information: <https://ffmpeg.org/legal.html>

The exact set of native libraries and their licenses depends on the selected
FFmpegKit artifact and build flags. Verify the artifact's own `NOTICE` /
license files before publishing a binary distribution.

## AI-generated content

Parts of this project were generated or substantially modified with AI
assistance. See the AI-generated content disclaimer in [README.md](README.md)
and [README.en.md](README.en.md). The disclaimer does not replace any license
terms or third-party obligations.
