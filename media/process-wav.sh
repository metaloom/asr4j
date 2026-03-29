#!/bin/bash

MODEL=openai/whisper-large-v3
MOVIE=$1
LANG=en
rm out.wav
ffmpeg -i $MOVIE -f wav -bitexact -acodec pcm_s16le -ar 22050 -ac 1 "out.wav"

curl http://localhost:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer EMPTY" \
  -F "model=$MODEL" \
  -F "language=$LANG" \
  -F "file=@out.wav"
