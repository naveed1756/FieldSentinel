# FieldSentinel

Offline Facial Recognition & Liveness Detection System for NHAI Field Personnel

FieldSentinel is an offline-first identity verification system designed for field personnel operating in remote and low-connectivity environments. The application performs on-device facial authentication using lightweight TensorFlow Lite models and securely stores authentication events locally until network connectivity is restored.

Developed for **NHAI Innovation Hackathon 7.0**.

---

## Overview

FieldSentinel enables secure personnel authentication without requiring continuous internet connectivity.

The system performs:

* Offline facial recognition
* Passive anti-spoofing
* GPS-tagged authentication events
* Encrypted biometric template storage
* Local SQLite event storage
* Deferred cloud synchronization
* Adaptive embedding drift updates

All biometric processing occurs entirely on-device.

---

## Key Features

### Offline Authentication

Authentication continues to function in:

* Remote project locations
* Highway construction zones
* Low-connectivity environments
* Airplane mode

No network access is required during authentication.

---

### Face Recognition

Uses MobileFaceNet to generate a 128-dimensional facial embedding.

* Model: MobileFaceNet INT8
* Size: 1.55 MB
* Output: 128-dimensional embedding vector
* Matching: Cosine Similarity

Only mathematical embeddings are stored.

Raw face images are never retained.

---

### Passive Anti-Spoofing

Uses MiniFASNet to identify:

* Printed photo attacks

* Screen replay attacks

* Basic spoof attempts

* Model: MiniFASNet

* Size: 0.88 MB

* Inference Time: ~30 ms

---

### Hardware-Backed Security

Embeddings are encrypted using:

* AES-256-GCM
* Android Keystore
* Hardware-backed key storage

Encryption keys never leave the device security boundary.

---

### GPS Verification

Authentication events include:

* Latitude
* Longitude
* GPS accuracy

Location acquisition works offline using device GPS.

---

### Local Storage

Authentication records are stored in:

```text
fieldsentinel.db
```

using SQLite.

Pending records remain available offline until synchronization succeeds.

---

### Sync & Purge

When connectivity is restored:

1. Pending records are uploaded
2. Records are marked as synced
3. Old synced records are purged

Network availability is monitored using NetInfo.

---

### Embedding Drift Adaptation

FieldSentinel updates reference embeddings after highly confident successful authentications.

Benefits:

* Adapts to appearance changes
* Reduces re-enrollment frequency
* Maintains recognition accuracy over time

---

## Architecture

```text
React Native UI
        │
        ▼
FaceAuth Bridge
        │
        ▼
Cascade Controller
        │
 ┌──────┼────────┐
 ▼      ▼        ▼
AntiSpoof  Recognition  Liveness
(MiniFASNet) (MobileFaceNet)
        │
        ▼
Authentication Result
        │
        ▼
SQLite Storage
        │
        ▼
AWS Sync Layer
```

---

## Authentication Pipeline

### Gate 1 — Face Detection

Validates face presence using confidence thresholds.

### Gate 2 — Passive Anti-Spoofing

Runs MiniFASNet.

Rejects obvious spoof attempts.

### Gate 3 — Active Liveness (Architecture)

Blink-based liveness verification is part of the system architecture but is not integrated in the current prototype.

### Gate 4 — Face Recognition

Generates a live embedding and compares it against the enrolled reference using cosine similarity.

---

## AI Models

### MobileFaceNet

| Property     | Value                 |
| ------------ | --------------------- |
| Type         | Face Recognition      |
| Quantization | INT8                  |
| Size         | 1.55 MB               |
| Input        | 112×112×3             |
| Output       | 128-D Embedding       |
| Accuracy     | 99.5% (LFW Benchmark) |

### MiniFASNet

| Property  | Value                 |
| --------- | --------------------- |
| Type      | Passive Anti-Spoof    |
| Size      | 0.88 MB               |
| Input     | 80×80×3               |
| Output    | Real/Spoof Confidence |
| Inference | ~30 ms                |

Total model footprint:

```text
2.43 MB
```

---

## Technology Stack

### Frontend

* React Native 0.73.6
* TypeScript

### Android Native Layer

* Kotlin 1.9.0
* React Native Native Modules

### AI Runtime

* TensorFlow Lite 2.14.0

### Storage

* SQLite
* AsyncStorage

### Security

* Android Keystore
* AES-256-GCM

### Connectivity

* NetInfo
* react-native-geolocation-service

---

## Prototype Status

### Implemented

* Offline facial authentication
* MobileFaceNet integration
* MiniFASNet anti-spoofing
* Confidence cascade
* GPS capture
* SQLite storage
* Network-aware sync
* Hardware-backed encryption
* Embedding drift updates

### Planned / Full Architecture

* MediaPipe face alignment
* MediaPipe face detection
* EAR-based blink liveness detection
* iOS native implementation
* Production AWS integration

---

## Build Requirements

* Node.js 22+
* React Native 0.73.6
* Android Studio
* JDK 17+
* Android SDK 34

---

## Installation

```bash
npm install
```

Run Android:

```bash
npx react-native run-android
```

Build Release APK:

```bash
cd android
gradlew.bat assembleRelease
```

Release APK:

```text
android/app/build/outputs/apk/release/app-release.apk
```

---

## Security Notes

* No raw facial images are stored.
* Embeddings are encrypted before persistence.
* Encryption keys remain inside Android Keystore.
* Authentication operates fully offline.
* GPS metadata is stored alongside authentication events.

---

## License

Created for NHAI Innovation Hackathon 7.0.

FieldSentinel Prototype v1.0

___
