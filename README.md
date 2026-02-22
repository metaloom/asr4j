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
  <version>0.0.1-SNAPSHOT</version>
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
CountDownLatch latch = new CountDownLatch(1);

String MOVIE = "media/jfk.webm";
String LANG = "en";
String MODEL_PATH = "models/ggml-large-v3-turbo.bin";

Whisper whisper = Whisper.create(MODEL_PATH);
// whisper.transcribe("movies/siw.das.fehlende.fragment.avi", "de");
whisper.transcribe(MOVIE, LANG).subscribe(new Flow.Subscriber<>() {

	@Override
	public void onSubscribe(Flow.Subscription subscription) {
		subscription.request(Long.MAX_VALUE); // request all items
	}

	@Override
	public void onNext(WhisperSegment item) {
		System.out.println("start: " + item.getStart());
		System.out.println("end: " + item.getEnd());
		System.out.println("text: " + item.getSentence());
	}

	@Override
	public void onError(Throwable throwable) {
		throwable.printStackTrace();
		latch.countDown();

	}

	@Override
	public void onComplete() {
		System.out.println("Transcription complete!");
		latch.countDown();

	}
});
latch.await();

```

### ASR vLLM - Transcribe Client

```java
String MOVIE = "media/jfk.webm";
ASRClient client = ASRClient.newBuilder()
	.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
	.setBaseURL("http://localhost:8000/v1").build();
HttpResponse<JsonObject> response = client.transcribe(MOVIE);
System.out.println(response.statusCode());
System.out.println(response.body().encodePrettily());
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
String MOVIE = "media/jfk.webm";
ASRClient client = ASRClient.newBuilder()
	.setModel(ASRClient.DEFAULT_VOXTRAL_MODEL_NAME)
	.setBaseURL("http://localhost:8000/v1").build();
client.realtime(MOVIE);
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

