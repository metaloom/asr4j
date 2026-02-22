#!/bin/bash

MODEL=openai/whisper-large-v3
MOVIE=jfk.webm
rm out.wav
#-ss 60 -t 60 
ffmpeg -i $MOVIE -f wav -bitexact -acodec pcm_s16le -ar 22050 -ac 1 "out.wav"

curl -vvv http://localhost:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer EMPTY" \
  -F "model=$MODEL" \
  -F "language=de" \
  -F "file=@out.wav"
