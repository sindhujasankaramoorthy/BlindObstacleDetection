from fastapi import FastAPI, File, UploadFile, Query
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
from PIL import Image
import io
import time

app = FastAPI(
    title="Blind Obstacle Detection AI Backend",
    version="1.1.0",
    description="High-Safety YOLO-powered object detection and spatial positioning service for visually impaired navigation."
)

# Enable CORS for local app/emulator connections
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

print("Loading YOLO Model...")
model = YOLO("yolov8n.pt")
print("YOLO Model Loaded Successfully!")

# High Priority Objects requiring immediate child-safety alert
HIGH_PRIORITY_OBJECTS = {
    "person", "car", "bus", "truck", "motorcycle", "bicycle",
    "stairs", "pole", "traffic light", "fire hydrant", "stop sign", "train",
    "chair", "table", "door", "bench", "potted plant", "dog", "cat"
}

# Distance ranking helper (lower number = closer/higher urgency)
DISTANCE_RANK = {
    "Very Close": 1,
    "Close": 2,
    "Medium": 3,
    "Far": 4
}


@app.get("/")
def home():
    return {
        "status": "online",
        "service": "Blind Obstacle Detection AI Navigation Assistant",
        "version": "1.1.0",
        "safety_level": "Child Safety Grade (High Precision)",
        "model": "YOLOv8n"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "timestamp": time.time()
    }


@app.post("/detect")
async def detect(
    file: UploadFile = File(...),
    conf_threshold: float = Query(0.25, ge=0.0, le=1.0)
):
    try:
        start_time = time.time()
        image_bytes = await file.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        image_width, image_height = image.size
        total_frame_area = image_width * image_height

        results = model.predict(
            source=image,
            conf=conf_threshold,
            verbose=False
        )

        detected_objects = []

        for result in results:
            for box in result.boxes:
                class_id = int(box.cls[0])
                confidence = float(box.conf[0])
                object_name = result.names[class_id]

                # Bounding box coordinates (pixels)
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                width = x2 - x1
                height = y2 - y1
                box_area = width * height
                area_ratio = box_area / float(total_frame_area) if total_frame_area > 0 else 0

                # -------------------------
                # Precision Distance & Human Step Estimation
                # -------------------------
                if area_ratio > 0.22 or box_area > 140000:
                    distance = "Very Close"
                    estimated_meters = 0.8
                    human_distance = "~1 step away"
                elif area_ratio > 0.09 or box_area > 65000:
                    distance = "Close"
                    estimated_meters = 1.5
                    human_distance = "~2-3 steps away"
                elif area_ratio > 0.025 or box_area > 25000:
                    distance = "Medium"
                    estimated_meters = 2.5
                    human_distance = "~4-5 steps away"
                else:
                    distance = "Far"
                    estimated_meters = 3.8
                    human_distance = "~6+ steps away"

                # -------------------------
                # Direction Estimation (3 regions & 5 regions)
                # -------------------------
                center_x = (x1 + x2) / 2.0
                norm_center_x = center_x / float(image_width) if image_width > 0 else 0.5

                # 3-Zone Classification
                if norm_center_x < 0.333:
                    direction = "Left"
                elif norm_center_x < 0.666:
                    direction = "Center"
                else:
                    direction = "Right"

                # 5-Zone Fine Classification
                if norm_center_x < 0.20:
                    fine_direction = "Far Left"
                elif norm_center_x < 0.40:
                    fine_direction = "Slight Left"
                elif norm_center_x < 0.60:
                    fine_direction = "Directly Ahead"
                elif norm_center_x < 0.80:
                    fine_direction = "Slight Right"
                else:
                    fine_direction = "Far Right"

                priority = "HIGH" if object_name.lower() in HIGH_PRIORITY_OBJECTS else "NORMAL"

                detected_objects.append({
                    "name": object_name,
                    "confidence": round(confidence, 2),
                    "distance": distance,
                    "estimated_meters": estimated_meters,
                    "human_distance": human_distance,
                    "direction": direction,
                    "fine_direction": fine_direction,
                    "priority": priority,
                    "bbox": [round(x1, 1), round(y1, 1), round(x2, 1), round(y2, 1)],
                    "norm_bbox": [
                        round(x1 / image_width, 4),
                        round(y1 / image_height, 4),
                        round(x2 / image_width, 4),
                        round(y2 / image_height, 4)
                    ],
                    "_dist_rank": DISTANCE_RANK.get(distance, 4)
                })

        # Sort Rule: Closest distance first, then highest priority, then highest confidence
        detected_objects.sort(key=lambda obj: (obj["_dist_rank"], 0 if obj["priority"] == "HIGH" else 1, -obj["confidence"]))

        # Remove temporary sorting field
        for obj in detected_objects:
            del obj["_dist_rank"]

        elapsed_ms = round((time.time() - start_time) * 1000, 1)

        return {
            "count": len(detected_objects),
            "objects": detected_objects,
            "image_width": image_width,
            "image_height": image_height,
            "processing_time_ms": elapsed_ms
        }

    except Exception as e:
        return {
            "error": str(e),
            "count": 0,
            "objects": []
        }