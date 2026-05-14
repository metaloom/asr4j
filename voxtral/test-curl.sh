#!/bin/bash

set -euo pipefail

MODEL_ID="mistralai/Voxtral-Mini-4B-Realtime-2602"
INPUT_WAV="${1:-../media/jfk_10s_pcm16.wav}"

if [[ ! -f "${INPUT_WAV}" ]]; then
  echo "Input file not found: ${INPUT_WAV}" >&2
  exit 1
fi

curl -sS http://localhost:8000/v1/audio/transcriptions \
  -H "Content-Type: multipart/form-data" \
  -F "file=@${INPUT_WAV}" \
  -F "model=${MODEL_ID}"
