# ASR4J

ASR4J provides various Automatic Speech Recognition (ASR) interface implementations to various ASR providers.

Supported providers:

| ASR Provider                  | Description                                                                |
|-------------------------------|----------------------------------------------------------------------------|
| Whisper.cpp                   | Uses native whisper.cpp to run inference locally                           |
| vLLM transcription endpoint   | Uses a http client to send the PCM data to the vLLM server                 |
| vLLM realtime endpoint        | Uses a websocket client to process PCM data for the vLLM realtime endpoint |


* Whisper.cpp
* vLLM transcription endpoint
* vLLM realtime endpoint  (WIP)

## Limitations

Currently only AMD64 Linux is supported. Support for other platforms is not planned.

## Usage

```xml
<dependency>
  <groupId>io.metaloom.asr4j</groupId>
  <artifactId>asr4j</artifactId>
  <version>${project.version}</version>
</dependency>
```

## License

The code of this project is "Apache License" but the license of the models may be different.

## Models

```

```

## Examples

### ASR Whisper.cpp

```java
%{snippet|id=whisper-usage.example|file=src/test/java/io/metaloom/asr/usage/UsageExampleTest.java}
```

### ASR vLLM - Transcribe Client

```java
%{snippet|id=transcribe-client-usage.example|file=src/test/java/io/metaloom/asr/usage/UsageExampleTest.java}
```

### ASR vLLM - Realtime Client

Please note that not all ASR models support this endpoint.

Voxtral Mini 4B supports this endpoint

```
. venv/bin/activate
export CUDA_VISIBLE_DEVICES=0,1
export VLLM_DISABLE_COMPILE_CACHE=1
vllm serve mistralai/Voxtral-Mini-4B-Realtime-2602 --compilation_config '{"cudagraph_mode": "PIECEWISE"}
```

```java
%{snippet|id=realtime-client-usage.example|file=src/test/java/io/metaloom/asr/usage/UsageExampleTest.java}
```
## Build 

### Requirements:

- JDK 25 or newer
- Maven

## Releasing

```bash
# Set release version and commit changes
mvn versions:set -DgenerateBackupPoms=false
git add pom.xml ; git commit -m "Prepare release"

# Invoke release
mvn clean deploy -Drelease
```

