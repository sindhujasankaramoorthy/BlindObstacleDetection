# 🚶 Blind Obstacle Detection & Walking Navigation System

An AI-powered native Android application engineered to empower visually impaired and blind users by providing **concurrent real-time camera obstacle detection** and **turn-by-turn pedestrian voice navigation**.

---

## 📌 Integrated Features (Phases 1–21 Complete)

### 🤖 1. Real-Time Obstacle Detection
- **CameraX Video Pipeline**: Seamless preview analysis with 640px downscaling and backpressure regulation.
- **Dual Inference Engine**:
  - Primary: On-device **ONNX Runtime** with YOLOv8n for zero-latency offline detection.
  - Secondary/Fallback: **FastAPI / Python** remote server backend for cloud inference.
- **Proximity & Direction Estimation**: Categorizes obstacles into **Very Close (<1m)**, **Close (1-2m)**, **Medium (2-3m)**, and **Far (>3m)** across Left, Center, and Right visual zones.
- **Haptic Alerts**: Multimodal vibration patterns (continuous emergency vibration for critical obstacles, pulses for warnings).

### 🎙️ 2. Voice-Controlled Destination Input (Phase 17)
- System speech recognizer converts spoken destinations to text.
- Spoken audio confirmation with TalkBack-accessible confirmation dialogs.
- Manual destination override and history persistence.

### 📍 3. Live High-Accuracy GPS & FusedLocation (Phase 18)
- Google Play Services `FusedLocationProviderClient` with high-accuracy updates (1000ms interval, 2m displacement).
- Continuous geocoding of current address using `GeocoderCompat`.
- Graceful degradation for poor satellite visibility and location permission denial.

### 🗺️ 4. Turn-by-Turn Pedestrian Walking Route Planning (Phase 19)
- OpenRouteService API integration for walking paths (`foot-walking` profile).
- Offline geographic polyline decoding into sequential waypoint coordinates.
- Step-by-step turn instructions with maneuver icons and distances.
- Built-in lightweight Canvas geometric route preview (zero external heavy map SDK overhead).

### 🔊 5. Intelligent Multi-Tier Speech Arbiter & Coordination (Phase 20)
- **Centralized Priority Queue (`SpeechManager.kt`)**:
  - **Tier 4: CRITICAL_OBSTACLE** (Emergency alerts, e.g. "Warning! Stop! Chair directly ahead very close") — *Immediately flushes active speech (`QUEUE_FLUSH`) and sounds emergency vibration*.
  - **Tier 3: IMPORTANT_OBSTACLE** (Nearby obstacles within walking corridor) — *Preempts navigation speech*.
  - **Tier 2: NAVIGATION_MANEUVER** (Step guidance, upcoming turns) — *Buffered with 8-second time-to-live (TTL); obsolete steps discarded if pre-empted*.
  - **Tier 1: STATUS_MESSAGE** (GPS lock, network feedback).
- **Mute Isolation**: Dedicated "Mute Navigation Voice" toggle that silences routine turn-by-turn instructions while keeping camera obstacle warnings and emergency haptics active.

### 🛡️ 6. Full System Integration & Safety Fallbacks (Phase 21)
- **Concurrent Live HUD**: Active navigation banner rendered directly on the camera preview screen with real-time turn guidance and live bounding boxes.
- **Monotonic Progression Protection**: Prevents GPS jitter from jumping navigation steps backward.
- **Off-Route Detection & Auto-Recalculate**: Triggers deviation alert when distance to path exceeds 35m across 3 consecutive fixes (with 25s announcement cooldown).
- **Graceful Sensor Handling**: Clear audio and visual banners when camera hardware is unavailable or location permissions are revoked.

---

## 🏗️ System Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │            Android Client (Jetpack Compose)  │
                  └───────┬───────────────────────────────┬──────┘
                          │                               │
                CameraX Image Analysis          FusedLocationProvider (GPS)
                          │                               │
            ┌─────────────┴─────────────┐                 │
            ▼                           ▼                 ▼
   On-Device ONNX Runtime       FastAPI Backend      Pedestrian Routing
       (yolov8n.onnx)            (Cloud YOLO)     (OpenRouteService API)
            │                           │                 │
            └─────────────┬─────────────┘                 │
                          ▼                               ▼
                 Detected Objects               Turn-by-Turn Steps
                          │                               │
                          └───────────────┬───────────────┘
                                          ▼
                         ┌─────────────────────────────────┐
                         │   Centralized SpeechManager     │
                         │   (4-Tier Priority Preemption)  │
                         └────────────────┬────────────────┘
                                          ▼
                         Text-to-Speech + Haptic Vibrator
```

---

## 🛠️ Tech Stack & Requirements

- **Android SDK**: `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`
- **Language**: Kotlin 2.0.0
- **UI Framework**: Jetpack Compose (Material 3, Glassmorphism design system)
- **Camera**: AndroidX CameraX (Preview + ImageAnalysis)
- **Machine Learning**: Microsoft ONNX Runtime Mobile (`onnxruntime-android:1.18.0`) + YOLOv8n
- **Location & Maps**: Google Play Services Fused Location + Geocoder
- **Routing**: OpenRouteService (REST JSON API with OkHttp / Retrofit)
- **Local Storage**: Jetpack DataStore Preferences

---

## ⚙️ Setup & Building

### 1. OpenRouteService API Key (Pedestrian Routing)
Add your free API key to `local.properties` (which is git-ignored):
```properties
ORS_API_KEY=your_openrouteservice_api_key_here
```
*(Alternatively, configure the key within the application UI via the "Set Key" button on the Walking Route Card).*

### 2. Compilation and Unit Testing
Run compilation and the complete test suite:
```bash
./gradlew testDebugUnitTest
```
All unit tests validate:
- Geodesic math, cross-track point projection, and monotonic step progression
- 4-tier speech arbitration and pre-emption logic
- Navigation muting isolation and obstacle priority
- Sensor loss, weak GPS filtering, and boundary route conditions

### 3. Generate Debug APK
Build the release-ready debug APK:
```bash
./gradlew assembleDebug
```
The compiled artifact is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## ♿ Accessibility & Safe Failure Behavior

1. **TalkBack Optimized**: All interactive components implement semantics (`contentDescription`, `Role.Button`, accessible state labels).
2. **High-Contrast Indicators**: Curated color tokens (Emerald, Sapphire, Warning Red) accompanied by explicit textual and spoken descriptions.
3. **Fail-Safe Warnings**: When GPS accuracy is weak (>30m) or camera capture fails, the system immediately announces the failure rather than presenting stale coordinates or false safety assurances.
4. **Safety Disclaimer**: This application is assistive software. It cannot guarantee detection of all ground hazards (stairs, drop-offs, clear glass) and must always be used alongside traditional orientation and mobility aids (e.g., white cane, guide dog).

---

## 🧪 Device Verification Checklist (Manual Testing)

When deploying `app-debug.apk` to a physical Android device:
- [ ] **Permissions Prompt**: Verify Camera, Location (Precise), and Audio permissions request smoothly.
- [ ] **Outdoor GPS Lock**: Verify blue location badge displays current street address.
- [ ] **Voice Destination**: Tap "Speak Destination", state a destination, and confirm.
- [ ] **Walking Navigation**: Tap "Start Walking Navigation" and walk ~20 meters; observe maneuver announcements.
- [ ] **Live Obstacle Detection**: Tap "View Live Camera Obstacle HUD" during navigation; observe simultaneous turn instructions and bounding box obstacle alerts.
- [ ] **Priority Preemption**: Stand in front of an obstacle while a turn maneuver is announced; verify the obstacle warning immediately cuts off the navigation speech.
- [ ] **Mute Navigation Voice**: Toggle "Mute Nav"; verify step instructions cease while obstacle speech remains audible.
