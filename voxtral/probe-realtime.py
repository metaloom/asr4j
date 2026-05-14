#!/usr/bin/env python3
"""Probe vLLM voxtral realtime to see what events come back per commit."""
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
        # mix down
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
    print(f"PCM bytes: {len(pcm)} ({len(pcm)/2/16000:.2f}s)", flush=True)

    async with websockets.connect(URL, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "model": MODEL}))
        # Split into two halves to simulate two utterances
        half = (len(pcm) // 2) & ~1
        chunks = [pcm[:half], pcm[half:]]

        async def recv():
            try:
                async for msg in ws:
                    ts = time.time()
                    try:
                        j = json.loads(msg)
                        t = j.get("type", "?")
                        # show short preview
                        preview = {k: (v if not isinstance(v, str) or len(v) < 120 else v[:120]+"...") for k, v in j.items() if k != "audio"}
                        print(f"[{ts:.3f}] EVENT type={t}  {preview}", flush=True)
                    except Exception:
                        print(f"[{ts:.3f}] RAW {msg[:200]}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: connection closed", flush=True)

        recv_task = asyncio.create_task(recv())

        for idx, chunk in enumerate(chunks, 1):
            print(f"\n--- sending chunk {idx} ({len(chunk)} bytes) ---", flush=True)
            # send in 320-byte frames like the Java client
            for off in range(0, len(chunk), 320):
                frame = chunk[off:off+320]
                await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                         "audio": base64.b64encode(frame).decode()}))
            await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
            print(f"--- chunk {idx} committed at {time.time():.3f} ---", flush=True)
            # wait between chunks to see whether chunk-1 gets emitted before chunk-2's commit
            await asyncio.sleep(6.0)

        # final wait for any trailing events
        await asyncio.sleep(4.0)
        await ws.close()
        await recv_task

asyncio.run(main())
