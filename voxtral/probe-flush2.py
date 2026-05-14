#!/usr/bin/env python3
"""Try cheaper flush options: bare extra commit, then input_audio_buffer.clear."""
import asyncio, base64, json, sys, time, wave, struct
import websockets

URL = "ws://localhost:8000/v1/realtime"
MODEL = "mistralai/Voxtral-Mini-4B-Realtime-2602"
WAV = sys.argv[1] if len(sys.argv) > 1 else "media/jfk_10s_pcm16.wav"
FLUSH_MODE = sys.argv[2] if len(sys.argv) > 2 else "bare-commit"  # bare-commit | clear | response-create | none

def read_pcm(path):
    with wave.open(path, "rb") as w:
        return w.readframes(w.getnframes())

async def main():
    pcm = read_pcm(WAV)
    seg = pcm[:1600 * 32]
    print(f"flush mode: {FLUSH_MODE}", flush=True)

    async with websockets.connect(URL, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "model": MODEL}))
        t0 = time.time()
        last = [t0]

        async def recv():
            try:
                async for msg in ws:
                    j = json.loads(msg)
                    typ = j.get("type", "")
                    delta = j.get("delta", "")
                    err = j.get("error")
                    if err:
                        print(f"[+{time.time()-t0:6.3f}s] ERROR type={typ} err={err}", flush=True)
                    elif delta:
                        print(f"[+{time.time()-t0:6.3f}s lag={time.time()-last[0]:5.3f}s] type={typ} delta={delta!r}", flush=True)
                    elif typ not in ("session.created", "session.updated"):
                        print(f"[+{time.time()-t0:6.3f}s] type={typ}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: closed", flush=True)
        recv_task = asyncio.create_task(recv())

        # Commit one real segment
        for off in range(0, len(seg), 320):
            await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                     "audio": base64.b64encode(seg[off:off+320]).decode()}))
        await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
        last[0] = time.time()
        print(f"[+{time.time()-t0:6.3f}s] committed REAL segment", flush=True)

        # Try the chosen flush trick
        if FLUSH_MODE == "bare-commit":
            await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
        elif FLUSH_MODE == "clear":
            await ws.send(json.dumps({"type": "input_audio_buffer.clear"}))
        elif FLUSH_MODE == "response-create":
            await ws.send(json.dumps({"type": "response.create"}))
        elif FLUSH_MODE == "none":
            pass
        last[0] = time.time()
        print(f"[+{time.time()-t0:6.3f}s] flush sent ({FLUSH_MODE})", flush=True)

        await asyncio.sleep(8.0)
        await ws.close()
        await recv_task

asyncio.run(main())
