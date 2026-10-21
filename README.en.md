[简体中文](README.md) | **English**

# CutGIF

> **AI-Generated Content Disclaimer**
>
> This project's code, documentation, screenshots, and test content were generated or substantially modified with AI assistance. They may contain errors, omissions, inaccurate details, or insecure implementations. Review, test, and validate everything before production, distribution, or commercial use. The project is provided **"as is"**, without warranties of any kind, express or implied. The authors accept no liability for any direct or indirect loss or damage arising from its use. Third-party components and referenced projects remain under their respective licenses.

An Android-native video-to-GIF and GIF-splitting tool built with **Jetpack Compose + Material 3 Expressive**.
It uses a single Activity, Compose Navigation, and ViewModel state hoisting. Video decoding, cropping, palette generation, and GIF encoding all happen on-device; nothing is uploaded.

Screenshots show the Chinese UI.

![Home](docs/screenshots/home.png)

## Features

- **Video to GIF**: pick a local video with the system Photo Picker and open the editing workspace.
- **Visual crop**: drag the four corners to adjust the crop rectangle; freeform, 1:1, 16:9, 9:16, and 4:3.
- **Filmstrip editing**: the time window is at most 60 seconds. Videos of 60 seconds or more use one cell per 10 seconds; shorter videos use six equal cells covering the full duration. Thumbnails have a fixed size and are center-cropped. Dragging slides thumbnails, 10-second ticks, and timestamps together like train cars while the time window and selection move together with a fixed length. Start and end handles always stay inside the viewport. Dragging uses local filmstrip state at 60fps and commits to the ViewModel every 80ms, avoiding whole-editor recomposition. Tick text is pre-laid out and cached. Selection range: 0.1–60 seconds.
- **Fine trim**: adjust the selected start or end point in 0.1-second steps; any pointer change resets the playhead to the start.
- **Preview playback**: ExoPlayer preview loops the playhead between the in/out points; discrete speeds from 0.25× to 4×.
- **GIF export**:
  - 144P / 240P / 360P / custom resolution
  - Low / medium / high / highest clarity and color quality
  - 5 / 10 / 15 / 25 / 30 / 50 / 60 FPS
  - Live progress, cancellation, sharing, and save path
  - In-app looping animation preview after export
- Files are saved to the system gallery under `Pictures/CutGIF` (Android 10+, via MediaStore).
- **GIF split / edit**: pick a GIF to open a frame-axis workspace. The strip shows one preview every 10 frames; drag the start/end frames, nudge one frame at a time, and preview-play the selected range. Export options change to color quality (low / medium / high / highest) and output scale (original / 75% / 50% / 25%). The result is still a looping GIF with the original frame delays, saved to `Pictures/CutGIF/CutGIF_<timestamp>.gif` and ready to share. Transparent pixels are filled with white.

| Editing workspace | Crop ratio |
| --- | --- |
| ![Editing workspace](docs/screenshots/editor.png) | ![Crop ratio](docs/screenshots/aspect.png) |

Continuous 60-second window: after dragging, the cars, 10-second ticks, and timestamps move together; the selection is still `1:00` and both handles remain visible:

![60-second filmstrip](docs/screenshots/filmstrip-end.png)

| Preview speed | Export settings |
| --- | --- |
| ![Preview speed](docs/screenshots/speed.png) | ![Export settings](docs/screenshots/export.png) |

![Export complete](docs/screenshots/export-success.png)

GIF split / edit workspace: one preview every 10 frames, draggable start/end frames, and an export sheet with color quality and output scale. The result is still a GIF:

![GIF split](docs/screenshots/gif-split.png)

## Design

- **Light-only Material 3 colors**: uses the specified primary / secondary / tertiary / surface / outline / error roles. All UI components read colors from `MaterialTheme.colorScheme`.
- **Shapes**:
  - Buttons default to pill shapes (half their height).
  - Cards, previews, and the filmstrip use 20dp corners.
  - Bottom sheets, export panels, and confirmation dialogs use 28dp corners.
  - Connected Button Groups use 3dp gaps, 8dp inner corners, and pill-shaped outer corners.
- **Typography**: system Roboto and the Material 3 type scale (`titleLarge`, `titleMedium`, `labelLarge`, `bodyMedium`, etc.).
- **Motion**: pages spring in from the right and slide out on back; buttons scale on press with Material ripple.
- **Dimensions**: centralized in `CutGifDimens` and `CutGifShape`, following an 8dp grid. The 44dp fine-tune buttons and 3dp connected spacing use dedicated tokens.

## Project structure

```
app/src/main/java/com/fgxyx/cutgif/
├── MainActivity.kt               # Single Activity + edge-to-edge
├── data/
│   ├── VideoFrames.kt            # Metadata, thumbnails, orientation
│   ├── GifExportRepository.kt    # Export, MediaStore, sharing
│   ├── FfmpegGifExporter.kt      # FFmpeg pipeline, AV1/libdav1d, progress
│   ├── GifFrameCounter.kt        # GIF frame-count validation
│   ├── GifMetadata.kt            # GIF size, frame count, frame delays
│   ├── GifFrameRenderer.kt       # Render a GIF frame by index
│   ├── GifSplitRepository.kt     # GIF re-encode, MediaStore, sharing
│   ├── ExportModels.kt           # Export parameters and results
│   └── CropRectN.kt              # Normalized crop rect
├── gif/
│   ├── GifEncoder.kt             # GIF89a / LZW / frame delays (legacy fallback)
│   └── ColorQuantizer.kt         # Median cut + ordered dithering + cache
├── ui/
│   ├── CutGifApp.kt              # Compose Navigation and page transitions
│   ├── home/HomeScreen.kt
│   ├── editor/                   # Video workspace, filmstrip, crop, bottom sheets, ViewModel
│   ├── split/                    # GIF split frame axis, export sheet, ViewModel
│   ├── components/               # Connected Group, press scale
│   ├── theme/                    # Palette, Shape, Dimens
│   └── util/                     # Time formatting
app/src/test/java/com/fgxyx/cutgif/gif/GifEncoderTest.kt
app/src/androidTest/java/com/fgxyx/cutgif/HomeScreenTest.kt
```

## Build and run

Current version: **1.0.0** (shown as `v1.0.0` in the top-right corner of the home screen).

Requirements:

- JDK 17+
- Android SDK Platform 37 / Build Tools 36
- Device or emulator with API 26+

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Implementation notes

- **Player**: ExoPlayer + SurfaceView with muted preview. Playback pauses when a bottom sheet opens or the crop is adjusted, reducing composition pressure during sheet animations. On emulators with `ranchu` hardware, a software decoder is preferred to avoid some H.264 hardware-decoder issues; physical devices use the default hardware decoder with decoder fallback enabled.
- **Crop**: the crop rectangle is stored as normalized coordinates in the ViewModel. During export it is applied to real video pixels and then scaled, so fixed ratios stay pixel-accurate.
- **GIF encoding (primary path)**: the maintained **FFmpegKit video** variant (with libdav1d / libvpx software decoders). AV1 sources explicitly use `-c:v libdav1d` and no longer depend on the device's hardware AV1 decoder. FFmpeg then resamples with `fps=<target>`, crops/scales with Lanczos, and encodes with `palettegen` / `paletteuse`.
- **AV1 compatibility**: some Android devices have no hardware AV1 decoder. The FFmpegKit **min** variant only supports MediaCodec and fails on AV1; this project uses the **video** variant and forces libdav1d software decoding.
- **Export acceleration**:
  - No per-frame `MediaMetadataRetriever` extraction. The video is decoded once by FFmpeg; its `fps` filter handles 30→25, 60→25, and other resampling.
  - The palette is generated on the Android side from three representative frames as a 256-pixel PNG; FFmpeg only performs the main encode. This avoids AV1 sources stalling in a separate palette pass.
  - If local palette generation fails, the exporter falls back to the FFmpeg palette pass.
  - Progress reports copying, palette generation, and FFmpeg encoding stages; a throttled heartbeat keeps the UI moving when FFmpeg is slow to report statistics, avoiding a permanent 0%.
  - Progress, cancellation, temporary-file cleanup, and MediaStore saving are managed by the export coroutine.
  - If FFmpeg initialization fails on a device, the exporter falls back to the legacy MediaMetadataRetriever + Kotlin encoder and disables indexed batching when frame-count metadata is implausible.
- **Frame-rate accuracy**: FFmpeg's `fps` filter outputs the target rate. GIF delays use 1/100-second units: 4cs for 25 FPS, 3cs for 30 FPS. Preview speed and export FPS are independent.
- **GIF split / edit**: `GifMetadataReader` parses the canvas size, total frame count, and per-frame delays; `GifFrameRenderer` seeks to each frame's start time. The frame axis loads a thumbnail every 10 frames, maps start/end frame numbers across the strip, and supports drag plus single-frame nudging. Export re-encodes the selected range as a looping GIF and preserves the original frame delays; color quality controls palette size and dithering, and transparent pixels are filled with white. On Android 10+, MediaStore writes to `Pictures/CutGIF/CutGIF_<timestamp>.gif`; older versions write to the app's external pictures directory and share through FileProvider.
- **State hoisting**: playback state, in/out points, selected pointer, crop ratio, speed, export configuration, and export progress are owned by `EditorViewModel`; the GIF split range, current frame, color quality, and output scale are owned by `GifSplitViewModel`.
- **No network permission**: all processing is local.
- **Large-file hint**: long or high-FPS GIFs are naturally large; the export sheet warns above an estimated 600 frames and suggests lowering the frame rate or shortening the range.

## Testing

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Tests include:

- Readability, frame count, dimensions, and trailer validation for globally paletted GIFs
- Quantizer color-count limit and index-validity checks
- Compose UI instrumentation for the home entry points and the "coming soon" Snackbar (`app.androidTest`)

> In Android Studio, use the **app** run configuration for the main app and **app.androidTest** for instrumentation tests. If the IDE still says it cannot obtain the package, run `File → Sync Project with Gradle Files` and retry.

## Troubleshooting

Since 1.4.0, exports emit `CutGifExport` logs. The most important part is:

```text
EXPORT_START version=... sdk=... abis=... ffmpeg=...
ffmpeg engine=two-pass command=...
...
EXPORT_DONE engine=ffmpeg frames=... size=... elapsedMs=...
```

A fallback looks like:

```text
FFMPEG_FALLBACK engine=legacy reason=<exception>:<reason> elapsedMs=...
```

The success screen then shows a "compatibility export (slower)" warning. If that warning appears, FFmpeg did not run and the slow legacy path produced the file. Send the complete log from `EXPORT_START` through `FFMPEG_FALLBACK`, including the stack trace.

```bash
adb logcat -c
# reproduce one export in the app
adb logcat -d -s CutGifExport:V > cutgif-export.log
```

If the exported frame count looks wrong, also include:

- The original video file
- The exported GIF under `Pictures/CutGIF`
- The export resolution, clarity, color quality, and FPS
- A screen recording or screenshot of the filmstrip

## Third-party components

- [FFmpegKit maintained](https://github.com/ffmpegkit-maintained/ffmpeg) (LGPL-3.0, `ffmpeg-kit-video` variant): FFmpeg native decoding (including libdav1d AV1 software decoding), fps / crop / scale / palette filters, and GIF encoding.
- [FFmpeg](https://ffmpeg.org/) (LGPL): multimedia framework.
- smart-exception-java (BSD-3-Clause): exception-reporting dependency used by FFmpegKit.

The GIF export approach was informed by [CuteGIF](https://github.com/tasy5kg/CuteGIF), which uses FFmpeg `fps` + `palettegen` / `paletteuse`. This project is an independent implementation and uses the LGPL FFmpegKit variant.

## Notes

- The specified Material 3 palette is fully mapped to `ui/theme/Color.kt`; UI components contain no color literals.
- Page transitions, Connected Button Groups, and press-scale interactions are implemented natively in Compose.
- GitHub: https://github.com/FGXYX
- Development and agent conventions: [AGENT.md](AGENT.md).
- The UI is currently Chinese; this English README documents the same build.

---

**Language / 语言:** [简体中文](README.md) | [English](README.en.md)
