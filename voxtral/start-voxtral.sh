#!/bin/bash

NAME=vllm
VERSION=v0.20.2
GPU=0
IMAGE=vllm/vllm-openai:voxtral

MODEL=mistralai/Voxtral-Mini-4B-Realtime-2602
docker  rm -f $NAME || true

if [[ -z "${HF_TOKEN}" ]]; then
  echo "Please set the env HF_TOKEN"
  exit 10
fi

echo "Starting $IMAGE"
#--env "VLLM_CUDA_GRAPH_MODE=PIECEWISE" \
docker run -d  --device nvidia.com/gpu=$GPU \
    --env "HF_TOKEN=$HF_TOKEN" \
    --env "VLLM_DISABLE_COMPILE_CACHE=1" \
    -p 8000:8000 \
    --ipc=host \
    -v /extra/cache/huggingface:/root/.cache/huggingface \
    --name $NAME \
    $IMAGE \
    --model $MODEL \
    --gpu-memory-utilization 0.9 \
    --max-num-seqs 40 \
    --max-model-len 2048 \
    --compilation-config "{\"cudagraph_mode\": \"PIECEWISE\"}"
docker logs -f $NAME
