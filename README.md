# PPE Detection Mobile App 🦺
 
Real-time Personal Protective Equipment detection using on-device computer vision for workplace safety monitoring.

Android app that runs **YOLOv8** (or compatible YOLO-style) object detection **on-device** with [TensorFlow Lite](https://www.tensorflow.org/lite) and [CameraX](https://developer.android.com/training/camerax). The sample UI focuses on **workplace safety**: live checklist for helmet and high-visibility vest (labels depend on your `labels.txt` and trained model).

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Features

- Real-time camera preview with `ImageAnalysis` (RGBA frames fed to TFLite).
- Bounding boxes and labels drawn on a transparent overlay (`OverlayView`).
- Optional UI logic for **helmet** / **vest** detection states (class names must match your labels).
- Post-processing: confidence filter + **NMS** (non-maximum suppression) in `Detector`.

## Requirements

- **Android Studio** (recommended: recent stable) with **JDK 17** (typical for AGP 8.x).
- **minSdk 21**, **compileSdk / targetSdk 34** (see `app/build.gradle.kts`).
- A physical device or emulator with a camera for live preview.

 
## Architecture
 
```
┌─────────────────────────────────────────────┐
│           Android Application               │
├─────────────────────────────────────────────┤
│  CameraX Preview & Analysis Pipeline        │
│    ↓                                         │
│  Frame Buffer → Image Preprocessing         │
│    ↓                                         │
│  TFLite Interpreter (YOLOv8n)               │
│    ↓                                         │
│  Bounding Box Post-Processing (NMS)         │
│    ↓                                         │
│  UI Overlay Renderer + Alerts               │
└─────────────────────────────────────────────┘
```

## Quick start

1. Clone this repository and open the **Gradle project root** (the folder that contains `app/` and `settings.gradle.kts`) in Android Studio.

   ```bash
   git clone https://github.com/spritlesoftware/ppe-detection-android/
   cd ppe-detection-android
   ```

2. Place your TensorFlow Lite model and label file in **`app/src/main/assets/`**:
   - `model.tflite` — YOLOv8 (or compatible) export for TFLite.
   - `labels.txt` — one class name per line, order matching model output.

3. If your filenames differ, update **`Constants.kt`**:

   ```kotlin
   const val MODEL_PATH = "your_model.tflite"
   const val LABELS_PATH = "your_labels.txt"
   ```

4. Open the project in Android Studio and **Run** on a device or emulator.

## Custom models

- Input size is read from the interpreter’s **input tensor** at runtime (`Detector.setup()`), so many square YOLO inputs (e.g. 640×640) work without hardcoding.
- Output layout is assumed to match the reference implementation in `Detector` (channels × detections). If you change architecture, adjust `bestBox()` and tensor shapes accordingly.
- **Class names** in `labels.txt` must align with training. The overlay and checklist use names like `helmet`, `vest`, `no-helmet`, `no-vest`, `person` for display; adapt `MainActivity` / `OverlayView` if your taxonomy differs.

## Project layout

| Path | Role |
|------|------|
| `MainActivity.kt` | CameraX preview + analysis loop; permission handling; UI updates from detections. |
| `Detector.kt` | Loads TFLite model and labels; preprocesses bitmaps; runs inference; decodes boxes + NMS. |
| `OverlayView.kt` | Custom `View` that draws normalized bounding boxes and labels. |
| `BoundingBox.kt` | Normalized box + class metadata for one detection. |
| `Constants.kt` | Asset paths for model and labels. |

## Tuning detection

In `Detector`’s companion object:

- **`CONFIDENCE_THRESHOLD`** — minimum class confidence to keep a detection.
- **`IOU_THRESHOLD`** — NMS overlap threshold (higher = more aggressive suppression).

Thread count for the interpreter is set in `setup()` (`Interpreter.Options().numThreads`).

## Open source

This project is released under the **MIT License** — see [LICENSE](LICENSE). Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Links
 
- **Blog Post:** [Open Sourcing Our PPE Detection App](#)
- **Demo Video:** [YouTube](#)
 
## Contact
 
- **Issues:** [GitHub Issues](https://github.com/spritle-software/ppe-detection-app/issues)
- **Email:** info@spritle.com
- **Twitter:** [@SpritlSoftware](https://twitter.com/SpritlSoftware)
 

## Third-party libraries

- [TensorFlow Lite](https://github.com/tensorflow/tensorflow) and **TensorFlow Lite Support** (Apache 2.0).
- **AndroidX** (CameraX, AppCompat, etc.) — see Gradle files for versions.

## Acknowledgments

Original reference workflow and upstream ideas are aligned with community YOLOv8 + TFLite Android examples (e.g. [surendramaran/YOLOv8-TfLite-Object-Detector](https://github.com/surendramaran/YOLOv8-TfLite-Object-Detector)). Replace the clone URL in your fork’s README if you maintain a separate upstream.


