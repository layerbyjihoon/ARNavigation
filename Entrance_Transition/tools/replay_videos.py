"""Sample videos at 3 Hz, run the bundled ONNX and the unchanged Java transition module.

Outputs: original/overlay comparison MP4, per-observation CSV, masks NPZ, provenance JSON.
Immediate indoor score >= 0.75. Media time is used as a deterministic elapsedRealtime replay clock.
"""
import argparse
import colorsys
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import time

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / 'Entrance_Transition'
ASSETS = PROJECT / 'app/src/main/assets'
FPS = 3


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def palette():
    colors = [tuple(round(c * 255) for c in colorsys.hsv_to_rgb((i * 137.5 % 360) / 360, .65, .95))
              for i in range(150)]
    for label, rgb in {0: 'E85D95', 3: '2ED3A2', 5: 'AC80FF', 14: 'FF953F',
                       53: 'FFDD38', 59: 'FFDD38', 2: '3DBDFF', 4: '3C9C48', 17: '3C9C48'}.items():
        colors[label] = tuple(bytes.fromhex(rgb))
    return np.array(colors, dtype=np.uint8)


def probe(path):
    return json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_streams', '-show_format',
        '-of', 'json', str(path)]))


def read_frame(pipe, count):
    chunks = bytearray()
    while len(chunks) < count:
        chunk = pipe.read(count - len(chunks))
        if not chunk:
            if chunks:
                raise RuntimeError('Truncated decoded video frame')
            return None
        chunks.extend(chunk)
    return bytes(chunks)


def run_video(video, output, session, classpath, font):
    info = probe(video)
    stream = info['streams'][0]
    width, height = stream['width'], stream['height']
    rotation = next((s.get('rotation', 0) for s in stream.get('side_data_list', []) if 'rotation' in s), 0)
    if abs(rotation) % 180 == 90:
        width, height = height, width
    destination = output / video.stem
    destination.mkdir(parents=True, exist_ok=False)
    decoder = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', str(video), '-map', '0:v:0',
                                '-vf', f'fps={FPS}', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
                               stdout=subprocess.PIPE)
    java = subprocess.Popen(['java', '-cp', classpath, 'ReplayTransition', str(ASSETS / 'munhwagwan.json')],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    result_video = destination / (video.stem + '_transition.mp4')
    encoder = subprocess.Popen(['ffmpeg', '-v', 'error', '-n', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                                '-s', '1080x1160', '-r', str(FPS), '-i', 'pipe:0', '-an',
                                '-c:v', 'libx264', '-preset', 'fast', '-crf', '20', '-pix_fmt', 'yuv420p',
                                '-movflags', '+faststart', str(result_video)], stdin=subprocess.PIPE)
    colors = palette()
    mean = np.array([.485, .456, .406], dtype=np.float32)
    std = np.array([.229, .224, .225], dtype=np.float32)
    rows, masks = [], []
    started = time.monotonic()
    try:
        while True:
            raw = read_frame(decoder.stdout, width * height * 3)
            if raw is None:
                break
            image = Image.frombytes('RGB', (width, height), raw)
            resized = np.asarray(image.resize((512, 512), Image.Resampling.BILINEAR), dtype=np.float32) / 255
            values = ((resized - mean) / std).transpose(2, 0, 1)[None].copy()
            inference_start = time.monotonic()
            logits = session.run(['logits'], {'pixel_values': values})[0][0]
            inference_ms = (time.monotonic() - inference_start) * 1000
            logits -= logits.max(axis=0, keepdims=True)
            probability = np.exp(logits)
            probability /= probability.sum(axis=0, keepdims=True)
            labels = probability.argmax(0).astype(np.int16)
            labels[probability.max(0) < .35] = -1
            timestamp = round(len(rows) * 1000 / FPS)
            java.stdin.write(json.dumps(dict(timestampMs=timestamp, width=128, height=128,
                                             labels=labels.flatten().tolist()), separators=(',', ':')) + '\n')
            java.stdin.flush()
            line = java.stdout.readline()
            if not line:
                raise RuntimeError('Java transition replay terminated')
            result = json.loads(line)
            result['inferenceMs'] = round(inference_ms, 2)
            result['validPixelFraction'] = round(float(np.mean(labels >= 0)), 4)
            rows.append(result)
            masks.append(labels.astype(np.int16))
            panel = image.resize((540, 960), Image.Resampling.BILINEAR)
            rgba = np.zeros((128, 128, 4), dtype=np.uint8)
            valid = labels >= 0
            rgba[valid, :3] = colors[labels[valid]]
            rgba[valid, 3] = 120
            overlay = Image.fromarray(rgba).resize(panel.size, Image.Resampling.NEAREST)
            annotated = Image.alpha_composite(panel.convert('RGBA'), overlay).convert('RGB')
            canvas = Image.new('RGB', (1080, 1160), '#101820')
            canvas.paste(panel, (0, 40))
            canvas.paste(annotated, (540, 40))
            draw = ImageDraw.Draw(canvas)
            draw.text((12, 4), f'{video.name}  원본', font=font, fill='white')
            draw.text((552, 4), 'Entrance_Transition 세그먼테이션', font=font, fill='white')
            mode = dict(UNKNOWN='판정 중', OUTDOOR='실외', INDOOR='실내')[result['mode']]
            draw.text((15, 1007), f'{timestamp / 1000:.2f}s  {mode}', font=font, fill='white')
            draw.text((15, 1042), f'실내 점수 {result["insideScore"]:.3f} / 기준 {result["matchedGate"]} / 전환 0.750'
                      f' · 구조 유효 {result["validStructure"]}', font=font, fill='white')
            draw.text((15, 1077), '벽 분홍 · 바닥 초록 · 천장 보라 · 계단 노랑 · 문 주황 · 하늘 파랑',
                      font=font, fill='white')
            draw.text((15, 1112), '3Hz 영상 시각 재생 · 실내 점수 0.75 이상 즉시 전환 · 무음', font=font, fill='#BAC6D0')
            encoder.stdin.write(canvas.tobytes())
            if len(rows) % 15 == 0:
                print(f'{video.name}: {len(rows)} observations / {timestamp / 1000:.1f}s', flush=True)
    finally:
        decoder.stdout.close()
        java.stdin.close()
        java.stdout.close()
        encoder.stdin.close()
        codes = [decoder.wait(), java.wait(), encoder.wait()]
    if any(codes) or not rows:
        raise RuntimeError(f'Video replay failed: {codes}')
    with (destination / 'observations.csv').open('w', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    np.savez_compressed(destination / 'masks.npz', labels=np.stack(masks),
                        timestamp_ms=np.array([r['timestampMs'] for r in rows]))
    changes = [r for i, r in enumerate(rows) if i == 0 or r['mode'] != rows[i - 1]['mode']]
    report = dict(source=str(video.relative_to(ROOT)), sourceSha256=sha(video),
                  modelSha256=sha(ASSETS / 'segformer-b0-ade20k.onnx'),
                  profileSha256=sha(ASSETS / 'munhwagwan.json'), samplingHz=FPS,
                  frameCount=len(rows), sourceDurationSeconds=float(info['format']['duration']),
                  runtimeSeconds=round(time.monotonic() - started, 2),
                  policy='inside >= 0.75: INDOOR; below: OUTDOOR; invalid: UNKNOWN', clock='sampled video media time, not inference wall time',
                  validStructureFrames=sum(r['validStructure'] for r in rows),
                  indoorFrames=sum(r['mode'] == 'INDOOR' for r in rows),
                  maxInsideScore=max(r['insideScore'] for r in rows),
                  stateChanges=changes)
    (destination / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('videos', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    profile = json.loads((ASSETS / 'munhwagwan.json').read_text())
    assert profile['modelSha256'] == sha(ASSETS / 'segformer-b0-ade20k.onnx')
    gson = next(p for p in (Path.home() / '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.13.2'
                           ).rglob('gson-2.13.2.jar'))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(ASSETS / 'segformer-b0-ade20k.onnx'), options,
                                   providers=['CPUExecutionProvider'])
    font = ImageFont.truetype('/System/Library/Fonts/AppleSDGothicNeo.ttc', 26)
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='entrance-video-') as compiled:
        sources = [PROJECT / 'tools/ReplayTransition.java',
                   PROJECT / 'app/src/main/java/com/example/entrancetransition/EnvironmentTransitionController.java']
        primitives = PROJECT / 'app/src/main/java/com/example/arnavigation/entrance'
        sources += [primitives / name for name in ['SegmentationMask.java', 'StructureSignature.java']]
        subprocess.run(['javac', '--release', '17', '-cp', str(gson), '-d', compiled,
                        *map(str, sources)], check=True)
        for video in args.videos:
            run_video(video.resolve(), args.output.resolve(), session, compiled + ':' + str(gson), font)
        (args.output / 'provenance.json').write_text(json.dumps(
            {str(p.relative_to(ROOT)): sha(p) for p in sources}, indent=2) + '\n')


if __name__ == '__main__':
    main()
