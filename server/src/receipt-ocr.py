"""Read one local receipt with bundled neural OCR models; no external API."""
import contextlib
import json
import sys
import warnings
import cv2
import numpy as np
import onnxruntime
from PIL import Image, ImageOps
from rapidocr_onnxruntime import RapidOCR

onnxruntime.disable_telemetry_events()
cv2.setNumThreads(1)
Image.MAX_IMAGE_PIXELS = 25_000_000
warnings.simplefilter("error", Image.DecompressionBombWarning)

with Image.open(sys.argv[1]) as source:
    if source.format not in ("JPEG", "PNG"):
        raise ValueError("unsupported image format")
    image = ImageOps.exif_transpose(source).convert("RGB")
    image.thumbnail((4096, 4096))
    pixels = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)

# Models are bundled in the wheel; inference never downloads weights.
with contextlib.redirect_stdout(sys.stderr):
    engine = RapidOCR(det_limit_side_len=1600, intra_op_num_threads=1, inter_op_num_threads=1)
    result, _ = engine(pixels)

rows = []
for box, text, score in result or []:
    if score < .5:
        continue
    points = np.asarray(box)
    y = float(points[:, 1].mean())
    x = float(points[:, 0].min())
    height = max(1., float(points[:, 1].max() - points[:, 1].min()))
    rows.append((y, x, height, text))
rows.sort()
lines = []
for y, x, height, text in rows:
    if lines and abs(y - lines[-1][0]) < min(height, lines[-1][1]) * .6:
        lines[-1][2].append((x, text))
    else:
        lines.append([y, height, [(x, text)]])
text = "\n".join(" ".join(word for _, word in sorted(words)) for _, _, words in lines)
print(json.dumps({"text": text}, ensure_ascii=False))
