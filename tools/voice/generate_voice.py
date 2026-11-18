#!/usr/bin/env python3
"""Render the bundled voice clips from the word list.

Reads src/main/resources/com/vinnyvangogh/questpath/voice/lines.tsv (the same
list the plugin composes lines from; build_lines.py writes it from quests.json)
and writes one WAV per clip id beside it: voice/<clip>.wav, mono, silence
trimmed. 16-bit PCM by default; --ulaw writes 8-bit mu-law instead (half the
size; javax.sound.sampled decodes it, and VoiceLines.join converts every clip
to 16-bit PCM before joining).

Developer tool only, run offline before a release; never shipped in the jar
and never run by the plugin. The plugin only plays the files this writes.

Ported from Combat Vitals' tools/voice/generate_voice.py (same trim, resample
and write), with synthesis through mlx-audio as the Board's
kokoro_render_clips.py does: kokoro-onnx's espeak bundle is broken on macOS.

Voice: Kokoro-82M (https://huggingface.co/hexgrad/Kokoro-82M), Apache-2.0
weights and voices, voice "af_heart" -- the same voice as Combat Vitals. A
synthetic voice, not a recording or imitation of any real person.

Setup, once, on Apple silicon, in a scratch virtualenv:

    python3.12 -m venv "$TMPDIR/kokoro" && . "$TMPDIR/kokoro/bin/activate"
    pip install mlx-audio numpy

Then, from the repo root:

    python tools/voice/generate_voice.py            # every clip not yet rendered
    python tools/voice/generate_voice.py --all      # re-render everything
    python tools/voice/generate_voice.py --only item-bucket

Options: --rate 22050, --ulaw, --speed 1.0, --voice af_heart.
"""

import argparse
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VOICE_DIR = ROOT / "src/main/resources/com/vinnyvangogh/questpath/voice"
LIST = VOICE_DIR / "lines.tsv"
RATE = 22050
SILENCE = 0.02  # below this (of full scale) counts as silence when trimming
PAD = 0.04  # seconds of silence kept after each clip, so joined parts breathe
MODEL = "mlx-community/Kokoro-82M-bf16"

# Said mid-sentence; rendered with a trailing comma so the pitch does not fall
# as if the sentence ended there.
LIFTED = {"looking-for"}


def read_clips(path):
    """Clip id -> words, each id once, in list order."""
    clips = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        cells = line.split("\t")
        if len(cells) != 4:
            raise SystemExit(f"bad row in {path.name}: {line!r}")
        _kind, _key, clip, words = (c.strip() for c in cells)
        clips.setdefault(clip, words)
    return clips


def trim(samples, np, rate=RATE):
    loud = np.flatnonzero(np.abs(samples) > SILENCE)
    if loud.size == 0:
        return samples
    start = max(0, loud[0] - int(0.01 * rate))
    end = loud[-1] + 1
    return np.concatenate([samples[start:end], np.zeros(int(PAD * rate), dtype=samples.dtype)])


def resample(samples, rate, np, target=RATE):
    """Linear resample to target after a short moving-average low-pass."""
    if rate == target:
        return samples
    width = max(1, round(rate / target))
    smoothed = np.convolve(samples, np.ones(width) / width, mode="same")
    positions = np.arange(0, len(smoothed), rate / target)
    return np.interp(positions, np.arange(len(smoothed)), smoothed)


def ulaw(samples, np):
    """G.711 mu-law bytes from float samples in -1..1 (the segmented encoder, as audioop.lin2ulaw)."""
    s = np.round(np.clip(samples, -1.0, 1.0) * 32767).astype(np.int32)
    sign = np.where(s < 0, 0x80, 0)
    mag = np.minimum(np.abs(s), 32635) + 0x84
    exponent = np.clip(np.floor(np.log2(mag)).astype(np.int32) - 7, 0, 7)
    mantissa = (mag >> (exponent + 3)) & 0x0F
    code = ~(sign | (exponent << 4) | mantissa) & 0xFF
    return code.astype(np.uint8).tobytes()


def write(path, samples, np, rate=RATE, mulaw=False):
    peak = float(np.max(np.abs(samples))) or 1.0
    scaled = samples / peak * 0.8
    if not mulaw:
        pcm = (scaled * 32767).astype("<i2")
        with wave.open(str(path), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(rate)
            out.writeframes(pcm.tobytes())
        return
    data = ulaw(scaled, np)
    # The wave module only writes PCM, so the mu-law header is built by hand:
    # format tag 7, 8 bits a sample, with the 2-byte cbSize WAVE wants.
    import struct
    fmt = struct.pack("<HHIIHHH", 7, 1, rate, rate, 1, 8, 0)
    fact = struct.pack("<I", len(data))
    body = (b"WAVE" + b"fmt " + struct.pack("<I", len(fmt)) + fmt
            + b"fact" + struct.pack("<I", 4) + fact
            + b"data" + struct.pack("<I", len(data)) + data + (b"\0" if len(data) % 2 else b""))
    path.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)


def synthesise(text, clip, voice, speed, scratch):
    subprocess.run([sys.executable, "-m", "mlx_audio.tts.generate", "--model", MODEL, "--text", text,
                    "--voice", voice, "--speed", str(speed), "--lang_code", "a", "--output_path", str(scratch),
                    "--file_prefix", clip, "--join_audio"], check=True, capture_output=True)
    with wave.open(str(Path(scratch) / f"{clip}.wav")) as w:
        return w.getframerate(), w.readframes(w.getnframes()), w.getsampwidth()


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--voice", default="af_heart")
    parser.add_argument("--speed", type=float, default=1.0)
    parser.add_argument("--rate", type=int, default=RATE)
    parser.add_argument("--ulaw", action="store_true")
    parser.add_argument("--only")
    parser.add_argument("--all", action="store_true", help="re-render clips already on disk")
    args = parser.parse_args()

    import numpy as np

    clips = read_clips(LIST)
    total = rendered = 0
    with tempfile.TemporaryDirectory() as scratch:
        for clip, words in clips.items():
            path = VOICE_DIR / f"{clip}.wav"
            if args.only and clip != args.only:
                continue
            if path.exists() and not args.all and not args.only:
                total += path.stat().st_size
                continue
            text = words + "," if clip in LIFTED else words
            rate, raw, width = synthesise(text, clip, args.voice, args.speed, scratch)
            samples = (np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768 if width == 2
                       else np.frombuffer(raw, dtype="<f4").astype(np.float64))
            samples = trim(resample(samples, rate, np, args.rate), np, args.rate)
            write(path, samples, np, args.rate, args.ulaw)
            size = path.stat().st_size
            total += size
            rendered += 1
            print(f"{clip}: {words!r} {size} bytes")
    print(f"{len(clips)} clips ({rendered} rendered now), {total} bytes on disk")
    return 0


if __name__ == "__main__":
    sys.exit(main())
