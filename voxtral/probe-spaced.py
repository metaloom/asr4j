#!/usr/bin/env python3
"""Probe vLLM voxtral realtime with the user's actual pattern:
commit a short utterance, then wait ~6s of nothing, then commit again.
Shows whether transcript of utterance #1 arrives before utterance #2 is committed."""
import asyncio, base64, json, struct, sys, time, wave
import websockets

URL = "ws://localhost:8000/v1/realtime"
MODEL = "mistralai/Voxtral-Mini-4B-Realtime-2602"
WAV = sys.argv[1] if len(sys.argv) > 1 else "media/jfk_10s_pcm16.wav"

def read_pcm16_mono_16k(path):
    with wave.open(path, "rb") as w:
        assert w.getsampwidth() == 2 and w.getframerate() == 16000
        n_ch = w.getnchannels()
        data = w.readframes(w.getnframes())
        if n_ch == 1:
            return data
        out = bytearray()
        for i in range(0, len(data), 2 * n_ch):
            s = 0
            for c in range(n_ch):
                s += struct.unpack_from("<h", data, i + 2 * c)[0]
            s //= n_ch
            out += struct.pack("<h", s)
        return bytes(out)

async def main():
    pcm = read_pcm16_mono_16k(WAV)
    seg_bytes = 1600 * 32  # ~1.6s
    segments = [pcm[i:i+seg_bytes] for i in range(0, len(pcm), seg_bytes)][:3]
    print(f"PCM bytes: {len(pcm)}; {len(segments)} segments of ~{seg_bytes} bytes", flush=True)

    async with websockets.connect(URL, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "model": MODEL}))
        t0 = time.time()
        last_commit_t = [t0]

        async def recv():
            try:
                async for msg in ws:
                    j = json.loads(msg)
                    delta = j.get("delta", "")
                    typ = j.get("type", "")
                    if delta:
                        print(f"[+{time.time()-t0:6.3f}s lag={time.time()-last_commit_t[0]:5.3f}s] type={typ} delta={delta!r}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: closed", flush=True)

        recv_task = asyncio.create_task(recv())

        for idx, seg in enumerate(segments, 1):
            for off in range(0, len(seg), 320):
                frame = seg[off:off+320]
                await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                         "audio": base64.b64encode(frame).decode()}))
            await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
            last_commit_t[0] = time.time()
            print(f"[+{time.time()-t0:6.3f}s] committed segment #{idx} ({len(seg)} bytes)", flush=True)
            # Long pause — like a user pausing between phrases
            if idx < len(segments):
                print(f"[+{time.time()-t0:6.3f}s] sleeping 6s before next commit ...", flush=True)
                await asyncio.sleep(6.0)

        print(f"[+{time.time()-t0:6.3f}s] all commits sent; draining 8s ...", flush=True)
        await asyncio.sleep(8.0)
        await ws.close()
        await recv_task

asyncio.run(main())
