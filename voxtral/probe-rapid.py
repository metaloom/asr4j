#!/usr/bin/env python3
"""Probe vLLM voxtral realtime with rapid back-to-back commits to mimic
the Java client's pulse-capture behaviour."""
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
    # Slice into a few short ~1.6s segments with NO sleep between commits.
    # Keep total <=5s to avoid overloading the server.
    seg_bytes = 1600 * 32  # ~1.6s at 16kHz mono PCM16
    max_segments = 3
    segments = [pcm[i:i+seg_bytes] for i in range(0, len(pcm), seg_bytes)][:max_segments]
    print(f"PCM bytes: {len(pcm)}; {len(segments)} segments of ~{seg_bytes} bytes", flush=True)

    async with websockets.connect(URL, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "model": MODEL}))
        commit_ts = {}

        async def recv():
            t0 = time.time()
            try:
                async for msg in ws:
                    j = json.loads(msg)
                    delta = j.get("delta", "")
                    if delta:
                        # Print which commit this likely belongs to
                        last_commit = max(commit_ts.values()) if commit_ts else t0
                        print(f"[+{time.time()-t0:6.3f}s lag={time.time()-last_commit:5.3f}s] delta={delta!r}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: closed", flush=True)

        recv_task = asyncio.create_task(recv())
        t0 = time.time()
        for idx, seg in enumerate(segments, 1):
            for off in range(0, len(seg), 320):
                frame = seg[off:off+320]
                await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                         "audio": base64.b64encode(frame).decode()}))
            await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
            commit_ts[idx] = time.time()
            print(f"[+{time.time()-t0:6.3f}s] committed segment #{idx} ({len(seg)} bytes)", flush=True)
            # NO sleep — back-to-back like the Java client does

        await asyncio.sleep(15.0)
        await ws.close()
        await recv_task

asyncio.run(main())
