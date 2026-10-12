#!/usr/bin/env python3
"""Download the selected models and convert Places365 ResNet18 to ONNX."""

from __future__ import annotations

import hashlib
import sys
import urllib.request
from pathlib import Path

import onnx
import torch
from torchvision.models import resnet18


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DOWNLOADS = ROOT / "tools" / ".downloads"

PLACES_WEIGHTS_URLS = (
    "https://places2.csail.mit.edu/models_places365/resnet18_places365.pth.tar",
    # Public mirror used when the legacy CSAIL host is temporarily unavailable.
    "https://raw.githubusercontent.com/jiahao-mobi/places365/master/"
    "resnet18_places365.pth.tar",
)
CATEGORIES_URL = (
    "https://raw.githubusercontent.com/CSAILVision/places365/master/"
    "categories_places365.txt"
)
YOLO_URL = (
    "https://huggingface.co/perseusdg/doorway-door-people-window-seg/resolve/main/"
    "doorway_seg_yolo26n.onnx?download=true"
)
EXPECTED_SHA256 = {
    "resnet18_places365.pth.tar": "2f4759217d470da2b803f8f66cd4488a066406b555a5fb95ee9a4663f9f05588",
    "doorway_seg_yolo26n.onnx": "b960c75c9d5365c0fd6c6dc09e92da75342805e5a270b3724aadf6fbcc36772a",
}


def download(urls: str | tuple[str, ...], destination: Path) -> None:
    if destination.exists() and destination.stat().st_size > 0:
        print(f"reuse {destination.name} ({destination.stat().st_size / 1_000_000:.1f} MB)")
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    urls = (urls,) if isinstance(urls, str) else urls
    last_error: Exception | None = None
    for url in urls:
        try:
            print(f"download {destination.name} from {url}")
            urllib.request.urlretrieve(url, temporary)
            temporary.replace(destination)
            return
        except Exception as error:  # Try the documented mirror before failing.
            last_error = error
            temporary.unlink(missing_ok=True)
            print(f"  failed: {error}")
    raise RuntimeError(f"could not download {destination.name}") from last_error


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_download(path: Path) -> None:
    expected = EXPECTED_SHA256.get(path.name)
    if expected is not None and sha256(path) != expected:
        raise RuntimeError(f"checksum mismatch for {path.name}; delete it and retry")


def convert_places(weights_path: Path, output_path: Path) -> None:
    print("convert ResNet18-Places365 -> ONNX opset 17")
    checkpoint = torch.load(weights_path, map_location="cpu", weights_only=True)
    state_dict = checkpoint.get("state_dict", checkpoint)
    state_dict = {key.removeprefix("module."): value for key, value in state_dict.items()}
    model = resnet18(weights=None, num_classes=365)
    model.load_state_dict(state_dict, strict=True)
    model.eval()
    dummy = torch.zeros(1, 3, 224, 224)
    torch.onnx.export(
        model,
        dummy,
        output_path,
        input_names=["images"],
        output_names=["logits"],
        opset_version=17,
        do_constant_folding=True,
        dynamic_axes=None,
    )
    checked = onnx.load(output_path)
    onnx.checker.check_model(checked)


def validate_categories(path: Path) -> None:
    count = len([line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()])
    if count != 365:
        raise RuntimeError(f"expected 365 Places labels, got {count}")


def main() -> int:
    ASSETS.mkdir(parents=True, exist_ok=True)
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    weights = DOWNLOADS / "resnet18_places365.pth.tar"
    places_onnx = ASSETS / "resnet18_places365.onnx"
    categories = ASSETS / "categories_places365.txt"
    yolo_onnx = ASSETS / "doorway_seg_yolo26n.onnx"

    download(PLACES_WEIGHTS_URLS, weights)
    verify_download(weights)
    download(CATEGORIES_URL, categories)
    validate_categories(categories)
    if not places_onnx.exists():
        convert_places(weights, places_onnx)
    else:
        onnx.checker.check_model(onnx.load(places_onnx))
        print(f"reuse {places_onnx.name}")
    download(YOLO_URL, yolo_onnx)
    verify_download(yolo_onnx)
    onnx.checker.check_model(onnx.load(yolo_onnx))

    print("\nready")
    for path in (places_onnx, categories, yolo_onnx):
        print(f"  {path.relative_to(ROOT)}  sha256={sha256(path)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
