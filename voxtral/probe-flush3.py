#!/usr/bin/env python3
"""Test min silence flush size that triggers transcript flush."""
import asyncio, base64, json, sys, time, wave
import websockets

URL = "ws://localhost:8000/v1/realtime"
MODEL = "mistralai/Voxtral-Mini-4B-Realtime-2602"
WAV = sys.argv[1] if len(sys.argv) > 1 else "media/jfk_10s_pcm16.wav"
FLUSH_SEC = float(sys.argv[2]) if len(sys.argv) > 2 else 0.5

def read_pcm(path):
    with wave.open(path, "rb") as w:
        return w.readframes(w.getnframes())

async def main():
    pcm = read_pcm(WAV)
    seg = pcm[:1600 * 32]  # 1.6s real
    flush = b"\x00\x00" * int(16000 * FLUSH_SEC)
    print(f"flush silence: {FLUSH_SEC}s", flush=True)

    async with websockets.connect(URL, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "model": MODEL}))
        t0 = time.time()
        last = [t0]

        async def recv():
            try:
                async for msg in ws:
                    j = json.loads(msg)
                    delta = j.get("delta", "")
                    typ = j.get("type", "")
                    if delta:
                        print(f"[+{time.time()-t0:6.3f}s lag={time.time()-last[0]:5.3f}s] delta={delta!r}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: closed", flush=True)
        recv_task = asyncio.create_task(recv())

        # Real commit
        for off in range(0, len(seg), 320):
            await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                     "audio": base64.b64encode(seg[off:off+320]).decode()}))
        await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
        last[0] = time.time()
        print(f"[+{time.time()-t0:6.3f}s] committed real", flush=True)

        # Flush
        if len(flush) > 0:
            # Send as ONE append, not chunked
            await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                     "audio": base64.b64encode(flush).decode()}))
            await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
            last[0] = time.time()
            print(f"[+{time.time()-t0:6.3f}s] committed flush", flush=True)

        await asyncio.sleep(8.0)
        await ws.close()
        await recv_task

asyncio.run(main())
