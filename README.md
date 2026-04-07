# MApp

MApp is an Android ARCore mobile app that displays a real-time AR camera feed, renders a 3D `.obj` model, and measures the distance from the phone camera to a real-world surface.

## Project Overview

The app combines ARCore session management, continuous hit testing, distance estimation, and OpenGL rendering to make a virtual dissector model behave like a real object in perspective. The model scales based on the camera distance and can be moved independently using manual offsets.

## What the app includes

- `app/`
  - Android UI, permissions, and lifecycle handling
  - Reference overlay for hit test targeting
  - Distance reporting and debug UI
  - Manual object offset controls
- `renderer/`
  - OpenGL ES 3 renderer for the camera background and 3D object
  - OBJ model loading and fallback cube rendering
  - Object placement and distance-based scaling logic
- `ar-session/`
  - ARCore session creation and configuration
  - Display geometry synchronization
  - Plane finding and depth-enabled hit testing
- `distance-calculator/`
  - Screen-space hit testing from camera center or overlay coordinates
  - Camera-to-hit distance calculation
  - Fallback estimation using camera intrinsics when no reliable hit is available

## Key features

- Continuous ARCore `Frame.hitTest()` from a reference point on screen
- Distance calculation from camera pose to hit pose
- Inverse distance scaling so the virtual object shrinks as the camera moves away
- Manual X/Y offset controls for independent object placement
- Per-frame debug logging of distance and hit source

## Usage

1. Open the root folder in VS Code.
2. Allow camera permission when prompted.
3. Aim the device at a surface and use the reference overlay to position the hit ray.
4. Use the offset controls to move the virtual object independently.

## Notes for new VS Code sessions

- Open the `MApp` workspace root so Copilot can see all modules and source files.
- The entry point is `app/src/main/java/com/mapp/app/MainActivity.kt`.
- Renderer and distance logic are split into separate modules for clarity.
- `assets/models/des01671317.obj` is the default model loaded by the renderer.

## Supported devices

- Devices with ARCore support
- The app uses ARCore plane detection and depth-enabled hit-testing

## Why this repository structure helps Copilot

- Each module has a clear responsibility: session, distance, rendering, and app UI.
- The README documents the architecture and major workflows.
- Opening the root folder in VS Code gives Copilot full access to all source files, module boundaries, and build configuration.
Checking existing README content and project scope.


## README Content

```markdown
# MApp

MApp is an AR mobile application built on ARCore that renders a 3D object in the camera view and measures distance from the phone to real-world surfaces. The app uses ARCore hit testing and camera pose tracking to place and scale a virtual `.obj` model naturally in the scene.

## What the app does

- Runs an ARCore session and displays the live camera feed using OpenGL ES.
- Performs continuous hit testing from the screen center or a user-selected reference point.
- Computes distance from the phone camera to the detected real-world surface.
- Dynamically scales the virtual object so it appears smaller as the camera moves away, simulating real perspective.
- Allows manual adjustment of the object position using on-screen controls.
- Displays live distance and source information for debugging and user feedback.

## Included modules

### `app/`
- Main Android application module
- UI layout and controls
- Permission handling
- Session attach and lifecycle management
- Reference overlay for screen-based hit targeting
- Distance / source display
- Manual object offset controls

### `renderer/`
- OpenGL renderer for AR background and 3D object drawing
- Object loading from `assets/models/*.obj`
- Model placement and transformation
- Camera-locked orientation logic
- Distance-based scaling logic

### `ar-session/`
- ARCore session creation and configuration
- Display geometry handling
- Plane detection and depth-enabled hit testing support
- Safe resume/pause lifecycle support

### `distance-calculator/`
- Distance estimation via ARCore `Frame.hitTest`
- Plane / trackable hit pose distance calculation
- Intrinsics-based fallback when no hit is available
- Distance source tracking for UI display

## Key features

- **ARCore hit testing**: uses screen coordinates or reference overlay coordinates.
- **Distance measurement**: calculates Euclidean distance from camera pose to hit pose.
- **Perspective scaling**: object scale is inversely proportional to distance.
- **Manual object movement**: adjust object offset in X/Y independently from hit point.
- **Debug-friendly**: logs distance, hit source, and scale every frame.

## Assets
- `app/src/main/assets/models/des01671317.obj` (default dissector model)
- Fallback drawing uses a unit cube when the OBJ model is not found.

## Notes
- The app is designed to work on devices with ARCore support.
- Depth is configured for automatic mode to improve hit reliability.
- Current logic prefers ARCore hit test results and falls back to intrinsics estimation only when needed.

```

If you want, I can also expand this into a full README with installation and usage instructions.