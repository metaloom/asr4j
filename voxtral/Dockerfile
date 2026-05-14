FROM vllm/vllm-openai:v0.20.2
#cu130-nightly
#FROM pytorch/pytorch:2.10.0-cuda13.0-cudnn9-runtime

RUN pip install uv
#RUN uv pip uninstall torch torchvision torchaudio flashinfer-jit-cache --system
#RUN uv pip install torch torchaudio torchvision --index-url https://download.pytorch.org/whl/cu130 --system
#RUN uv pip install -U vllm \
#  --torch-backend=auto \
#  --extra-index-url https://wheels.vllm.ai/nightly/cu130 --system 

RUN uv pip install mistral-common[soundfile] soxr librosa soundfile nvidia-cuda-nvrtc --system
#RUN python -c "import mistral_common; print(mistral_common.__version__)"
#RUN uv pip install flashinfer-jit-cache --index-url https://flashinfer.ai/whl/cu130 --system
RUN pip list
