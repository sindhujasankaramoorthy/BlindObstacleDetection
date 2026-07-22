from fastapi import FastAPI, File, UploadFile
from ultralytics import YOLO
from PIL import Image
import io

app = FastAPI()

print("Loading YOLO Model...")
model = YOLO("yolov8n.pt")
print("YOLO Model Loaded Successfully!")


@app.get("/")
def home():
    return {
        "message": "Blind Obstacle Detection Backend Running"
    }


@app.post("/detect")
async def detect(file: UploadFile = File(...)):
    try:
        image_bytes = await file.read()

        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        results = model.predict(
            source=image,
            conf=0.25,
            verbose=False
        )

        detected_objects = []

        for result in results:

            print("\nDetected Objects:")

            for box in result.boxes:

                class_id = int(box.cls[0])
                confidence = float(box.conf[0])

                object_name = result.names[class_id]

                print(f"{object_name} ({confidence:.2f})")

                detected_objects.append({
                    "name": object_name,
                    "confidence": round(confidence, 2)
                })

        return {
            "count": len(detected_objects),
            "objects": detected_objects
        }

    except Exception as e:
        return {
            "error": str(e)
        }