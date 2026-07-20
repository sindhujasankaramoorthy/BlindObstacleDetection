from ultralytics import YOLO

model = YOLO("yolov8n.pt")

results = model.predict(
    "test chair.jpg",
    conf=0.05,      # Lower confidence
    imgsz=640,      # Resize input
    save=True
)

for result in results:
    print(result.boxes)