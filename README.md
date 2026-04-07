# MApp — ARCore distance demo (Galaxy A52–friendly)

**Application ID / package:** `com.mapp.app`

Kotlin multi-module sample: live ARCore camera background, **standard View overlay** (center dot + vertical line), plane **hit-test** distance with **intrinsics + assumed-size fallback** (no Depth API), and a **dynamically scaled** grey cube with **black edge lines** (OpenGL ES 3, no Filament/Sceneform).

## Modules

| Module | Role |
|--------|------|
| `:ar-session` | `ArSessionManager`, `ArCoreSupport` — session lifecycle, `Config.DepthMode.DISABLED`, display geometry |
| `:distance-calculator` | `DistanceCalculator` — center/tap `hitTest`, Euclidean camera↔hit distance, fallback depth formula |
| `:renderer` | `ArSceneGlRenderer`, `BackgroundRenderer`, `CubeOutlineRenderer` — GLES camera quad + cube |
| `:app` | `MainActivity`, `ReferenceOverlayView`, permissions, UI text |

## Requirements

- Android Studio **Ladybug** or newer (or AGP **8.7+**, JDK **17**)
- Physical **ARCore-supported** device with **Google Play Services for AR**
- **OpenGL ES 3.0**

## Open in Android Studio

1. **File → Open** and select the `MApp` folder (this directory: `.../repos/MApp`).
2. Let Gradle sync; if prompted, install missing SDK platforms/build-tools.
3. **Run** on a physical device (ARCore does not run on most emulators).

### Command line

From this directory:

```bash
./gradlew :app:assembleDebug
```

On Windows, generate the wrapper first if `gradlew.bat` is missing:

```powershell
gradle wrapper --gradle-version 8.9
.\gradlew.bat :app:assembleDebug
```

## Behaviour

- **Distance (primary):** Ray from the **center** of the `GLSurfaceView` (aligned with the red overlay) → `Frame.hitTest` → first **tracked plane** → Euclidean distance from **camera pose** to **hit pose**.
- **Distance (fallback):** If no plane is hit, uses **texture intrinsics** `fx` and an **assumed real-world width** vs **assumed visible fraction** of the image (`DistanceCalculator` — tunable fields). Does **not** use Depth API.
- **Tap (optional):** Tap the overlay to cast the ray from that pixel; **Center** resets aim to the middle.
- **Scale:** `scale = clamp(k * distanceMeters, min, max)` on `ArSceneGlRenderer` (`distanceToScaleK`, `minObjectScale`, `maxObjectScale`).

## Tuning for your scene

- In `MainActivity`, adjust `DistanceCalculator(assumedRealWidthMeters = …, assumedVisibleFractionOfImageWidth = …)`.
- In `ArSceneGlRenderer`, adjust `distanceToScaleK` / clamp range for cube size vs distance.

## Performance notes

- Single low-poly cube, simple solid + line shaders, `LATEST_CAMERA_IMAGE`, depth off.
- Target **24–30+ FPS** depends on device thermal state and scene complexity; avoid extra post-processing.

## Permissions & ARCore

- **Camera** is requested at runtime.
- `ArCoreSupport.ensureInstalled` handles install/update flows for Play Services for AR.
- Manifest includes `com.google.ar.core` metadata **required**; unsupported devices exit with a clear message.
