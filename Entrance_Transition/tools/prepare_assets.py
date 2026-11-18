"""Export the official OpenMMLab B0 checkpoint and derive Cultural Center references.

Inference-only adaptation of mmsegmentation's mit.py and segformer_head.py (Apache-2.0).
No training or generated labels: reference masks come from the actual ONNX output.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image, ImageOps
import torch
from torch import nn
from torch.nn import functional as F

CHECKPOINT_URL = (
    "https://download.openmmlab.com/mmsegmentation/v0.5/segformer/"
    "segformer_mit-b0_512x512_160k_ade20k/"
    "segformer_mit-b0_512x512_160k_ade20k_20210726_101530-8ffa8fda.pth"
)
MODEL_ID = "mmseg-segformer-b0-ade20k-512-v1"
CHECKPOINT_SHA256 = "8ffa8fdac140ffaef9119150aca65e9fcb4e7a165e60b80be0102cc805d73a9c"


class PatchEmbed(nn.Module):
    def __init__(self, channels, dim, kernel, stride):
        super().__init__()
        self.projection = nn.Conv2d(channels, dim, kernel, stride, kernel // 2)
        self.norm = nn.LayerNorm(dim, eps=1e-6)

    def forward(self, x):
        x = self.projection(x)
        h, w = x.shape[-2:]
        return self.norm(x.flatten(2).transpose(1, 2)), h, w


class Attention(nn.Module):
    def __init__(self, dim, heads, ratio):
        super().__init__()
        self.attn = nn.MultiheadAttention(dim, heads, batch_first=True)
        self.heads = heads
        self.ratio = ratio
        self.use_reference = False
        if ratio > 1:
            self.sr = nn.Conv2d(dim, dim, ratio, ratio)
            self.norm = nn.LayerNorm(dim, eps=1e-6)

    def forward(self, x, h, w):
        kv = x
        if self.ratio > 1:
            kv = self.sr(x.transpose(1, 2).reshape(x.shape[0], x.shape[2], h, w))
            kv = self.norm(kv.flatten(2).transpose(1, 2))
        if self.use_reference:
            return self.attn(x, kv, kv, need_weights=True)[0]
        # Explicit attention avoids unsupported fused-MHA ONNX operators.
        weights = self.attn.in_proj_weight.chunk(3)
        biases = self.attn.in_proj_bias.chunk(3)
        q, k, v = [F.linear(t, weight, bias) for t, weight, bias
                   in zip((x, kv, kv), weights, biases)]
        batch, count, dim = q.shape
        width = dim // self.heads
        q = q.reshape(batch, count, self.heads, width).transpose(1, 2)
        k = k.reshape(batch, -1, self.heads, width).transpose(1, 2)
        v = v.reshape(batch, -1, self.heads, width).transpose(1, 2)
        out = (q * (width ** -0.5) @ k.transpose(-2, -1)).softmax(-1) @ v
        return self.attn.out_proj(out.transpose(1, 2).reshape(batch, count, dim))


class MixFFN(nn.Module):
    def __init__(self, dim):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Conv2d(dim, dim * 4, 1),
            nn.Conv2d(dim * 4, dim * 4, 3, padding=1, groups=dim * 4),
            nn.GELU(), nn.Dropout(0), nn.Conv2d(dim * 4, dim, 1), nn.Dropout(0))

    def forward(self, x, h, w):
        return self.layers(x.transpose(1, 2).reshape(x.shape[0], x.shape[2], h, w)
                           ).flatten(2).transpose(1, 2)


class Block(nn.Module):
    def __init__(self, dim, heads, ratio):
        super().__init__()
        self.norm1 = nn.LayerNorm(dim, eps=1e-6)
        self.attn = Attention(dim, heads, ratio)
        self.norm2 = nn.LayerNorm(dim, eps=1e-6)
        self.ffn = MixFFN(dim)

    def forward(self, x, h, w):
        x = x + self.attn(self.norm1(x), h, w)
        return x + self.ffn(self.norm2(x), h, w)


class Backbone(nn.Module):
    def __init__(self):
        super().__init__()
        self.layers = nn.ModuleList()
        channels = 3
        for i, (dim, heads, ratio) in enumerate(zip((32, 64, 160, 256), (1, 2, 5, 8), (8, 4, 2, 1))):
            self.layers.append(nn.ModuleList([
                PatchEmbed(channels, dim, 7 if i == 0 else 3, 4 if i == 0 else 2),
                nn.ModuleList([Block(dim, heads, ratio) for _ in range(2)]),
                nn.LayerNorm(dim, eps=1e-6)]))
            channels = dim

    def forward(self, x):
        features = []
        for patch, blocks, norm in self.layers:
            x, h, w = patch(x)
            for block in blocks:
                x = block(x, h, w)
            x = norm(x).transpose(1, 2).reshape(x.shape[0], -1, h, w)
            features.append(x)
        return features


class ConvModule(nn.Module):
    def __init__(self, channels, dim):
        super().__init__()
        self.conv = nn.Conv2d(channels, dim, 1, bias=False)
        self.bn = nn.BatchNorm2d(dim, eps=1e-5)

    def forward(self, x):
        return F.relu(self.bn(self.conv(x)))


class Head(nn.Module):
    def __init__(self):
        super().__init__()
        self.convs = nn.ModuleList([ConvModule(dim, 256) for dim in (32, 64, 160, 256)])
        self.fusion_conv = ConvModule(1024, 256)
        self.conv_seg = nn.Conv2d(256, 150, 1)

    def forward(self, features):
        out = [F.interpolate(conv(feature), features[0].shape[-2:], mode="bilinear", align_corners=False)
               for conv, feature in zip(self.convs, features)]
        return self.conv_seg(self.fusion_conv(torch.cat(out, 1)))


class Segformer(nn.Module):
    def __init__(self):
        super().__init__()
        self.backbone = Backbone()
        self.decode_head = Head()

    def forward(self, pixel_values):
        return self.decode_head(self.backbone(pixel_values))


def tensor(image_path):
    # Android uses full-frame bilinear resize and the same RGB normalization.
    with Image.open(image_path) as image:
        image = ImageOps.exif_transpose(image).convert("RGB").resize((512, 512), Image.Resampling.BILINEAR)
        rgb = np.asarray(image, dtype=np.float32) / 255.0
    return ((rgb - np.array([.485, .456, .406], dtype=np.float32))
            / np.array([.229, .224, .225], dtype=np.float32)).transpose(2, 0, 1)[None].copy()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--reuse-model", action="store_true",
                        help="Regenerate references without re-exporting the existing ONNX")
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    assets = project / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    if not args.checkpoint.exists():
        urllib.request.urlretrieve(CHECKPOINT_URL, args.checkpoint)
    checkpoint_bytes = args.checkpoint.read_bytes()
    checkpoint_sha = hashlib.sha256(checkpoint_bytes).hexdigest()
    if checkpoint_sha != CHECKPOINT_SHA256:
        raise ValueError("Official checkpoint SHA-256 mismatch")
    torch.set_num_threads(2)
    model = Segformer().eval()
    # Checkpoint is fetched from the official source; strict=True rejects missing/random parameters.
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=False)
    model.load_state_dict(checkpoint["state_dict"], strict=True)
    model_path = assets / "segformer-b0-ade20k.onnx"
    if not args.reuse_model:
        with torch.no_grad():
            torch.onnx.export(model, torch.zeros(1, 3, 512, 512), str(model_path),
                              input_names=["pixel_values"], output_names=["logits"],
                              opset_version=17, dynamo=False)
    onnx.checker.check_model(onnx.load(model_path))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(model_path), options, providers=["CPUExecutionProvider"])
    profile = dict(schemaVersion=1, buildingId="ssu-munhwagwan", modelId=MODEL_ID,
                   viewPolicy="upright-full-frame", modelSha256=hashlib.sha256(model_path.read_bytes()).hexdigest(),
                   insideMasks=[], outsideMasks=[])
    report = dict(checkpointUrl=CHECKPOINT_URL, checkpointSha256=checkpoint_sha,
                  modelSha256=profile["modelSha256"], sourceImages=[])
    for name, group in [("gate1", "insideMasks"), ("gate2", "insideMasks"),
                        ("gate3", "insideMasks"), ("gate4", "insideMasks"),
                        ("avoidance", "outsideMasks"), ("avoidance2", "outsideMasks")]:
        image_path = project.parent / "assets" / (name + ".jpeg")
        values = tensor(image_path)
        logits = session.run(["logits"], {"pixel_values": values})[0]
        for module in model.modules():
            if isinstance(module, Attention):
                module.use_reference = True
        with torch.no_grad():
            reference = model(torch.from_numpy(values)).numpy()
        np.testing.assert_allclose(logits, reference, rtol=2e-3, atol=2e-3)
        # Fixed export already returns 128x128, matching Java SegformerLogits decoding.
        probabilities = torch.softmax(torch.from_numpy(logits[0]), dim=0)
        confidence, labels = probabilities.max(0)
        labels[confidence < .35] = -1
        profile[group].append(dict(referenceId=name, width=128, height=128,
                                   labels=labels.flatten().tolist()))
        item = dict(file=name + ".jpeg", sha256=hashlib.sha256(image_path.read_bytes()).hexdigest(),
                    maxLogitError=float(np.max(np.abs(logits - reference))),
                    classAgreement=float(np.mean(logits.argmax(1) == reference.argmax(1))))
        report["sourceImages"].append(item)
        print(item, flush=True)
    (assets / "munhwagwan.json").write_text(json.dumps(profile, separators=(",", ":")))
    (project / "tools/asset-validation.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
