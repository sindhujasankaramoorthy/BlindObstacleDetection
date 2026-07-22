# 🚶 Blind Obstacle Detection System

An AI-powered Android application that assists visually impaired users by detecting nearby objects in real time. The application captures live camera frames, sends them to a FastAPI backend running a YOLOv8 model, and provides both visual and voice feedback.

---

## 📌 Features

- 📷 Real-time camera preview using CameraX
- 🤖 AI-based object detection using YOLOv8n
- 🌐 FastAPI backend for inference
- 📡 Client-server communication using Retrofit
- 🖼️ Real-time image processing
- 🎯 Multiple object detection
- 📊 Confidence score display
- 🔊 Text-to-Speech (TTS) voice feedback
- 🔁 Duplicate speech prevention
- ⚡ Live detection every second
- ❌ Error handling for backend connectivity

---

## 🏗️ System Architecture

```
Android App
     │
     ▼
CameraX
     │
     ▼
Image Processing
     │
     ▼
Retrofit API
     │
     ▼
FastAPI Backend
     │
     ▼
YOLOv8n Model
     │
     ▼
Detection Results
     │
     ▼
Android UI + Text-to-Speech
```

---

## 🛠️ Technologies Used

### Android
- Kotlin
- Jetpack Compose
- CameraX
- Retrofit
- OkHttp
- Coroutines
- Material 3

### Backend
- Python
- FastAPI
- Uvicorn
- YOLOv8 (Ultralytics)

### AI
- YOLOv8n Object Detection Model

---

## 📂 Project Structure

```
BlindObstacleDetection/
│
├── app/                 # Android application
├── backend/             # FastAPI backend
│   ├── main.py
│   ├── yolov8n.pt
│   └── requirements.txt
│
├── README.md
└── .gitignore
```

---

## ⚙️ Installation

### Clone Repository

```bash
git clone https://github.com/sindhujasankaramoorthy/BlindObstacleDetection.git
cd BlindObstacleDetection
```

---

### Backend Setup

```bash
cd backend

python -m venv venv

venv\Scripts\activate

pip install -r requirements.txt
```

Run the server:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

---

### Android Setup

1. Open the project in Android Studio.
2. Connect your Android device to the same Wi-Fi network as the backend.
3. Update the backend IP address in:

```
RetrofitClient.kt
```

Example:

```kotlin
private const val BASE_URL = "http://192.168.1.35:8000/"
```

4. Build and run the application.

---

## 📱 Application Workflow

1. Open the application.
2. Tap **Start Detection**.
3. Live camera preview starts.
4. Camera frames are captured.
5. Frames are sent to the FastAPI server.
6. YOLOv8 detects objects.
7. Detection results are returned.
8. The application:
   - Displays detected objects.
   - Shows confidence scores.
   - Announces detected objects using Text-to-Speech.

---

## 📷 Sample Output

```
Chair (94%)
Bottle (91%)
Person (98%)
```

Voice Output:

```
Chair
Bottle
Person
```

---

## 🚀 Future Enhancements

- Distance estimation
- Obstacle direction guidance
- Offline YOLO inference using TensorFlow Lite
- GPS-based navigation
- Bluetooth headset support
- Haptic vibration alerts

---

## 👨‍💻 Developed By

**Sindhuja Sankaramoorthy**

