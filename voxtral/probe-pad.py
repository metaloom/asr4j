#!/usr/bin/env python3
"""Test: real audio + N seconds of trailing silence in ONE commit.
Does voxtral flush the transcript right after commit?"""
import asyncio, base64, json, sys, time, wave
import websockets

URL = "ws://localhost:8000/v1/realtime"
MODEL = "mistralai/Voxtral-Mini-4B-Realtime-2602"
WAV = sys.argv[1] if len(sys.argv) > 1 else "media/jfk_10s_pcm16.wav"
PAD_SEC = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0

def read_pcm(path):
    import struct
    with wave.open(path, "rb") as w:
        n_ch = w.getnchannels()
        data = w.readframes(w.getnframes())
        if n_ch == 1:
            return data
        out = bytearray()
        for i in range(0, len(data), 2 * n_ch):
            s = sum(struct.unpack_from("<h", data, i + 2 * c)[0] for c in range(n_ch)) // n_ch
            out += struct.pack("<h", s)
        return bytes(out)

async def main():
    pcm = read_pcm(WAV)
    seg = pcm[:1600 * 32]  # 1.6s real
    pad = b"\x00\x00" * int(16000 * PAD_SEC)
    audio = seg + pad
    print(f"audio: {len(seg)} real + {len(pad)} silence ({PAD_SEC}s pad)", flush=True)

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
                        print(f"[+{time.time()-t0:6.3f}s lag={time.time()-last[0]:5.3f}s] {typ} delta={delta!r}", flush=True)
            except websockets.ConnectionClosed:
                print("recv: closed", flush=True)
        recv_task = asyncio.create_task(recv())

        for off in range(0, len(audio), 320):
            await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                     "audio": base64.b64encode(audio[off:off+320]).decode()}))
        await ws.send(json.dumps({"type": "input_audio_buffer.commit"}))
        last[0] = time.time()
        print(f"[+{time.time()-t0:6.3f}s] committed", flush=True)

        await asyncio.sleep(8.0)
        await ws.close()
        await recv_task

asyncio.run(main())
