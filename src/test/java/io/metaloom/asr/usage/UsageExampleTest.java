package io.metaloom.asr.usage;

import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

import io.github.ggerganov.whispercpp.bean.WhisperSegment;
import io.metaloom.asr.client.ASRClient;
import io.metaloom.asr.whisper.Whisper;
import io.vertx.core.json.JsonObject;

public class UsageExampleTest {

	@Test
	public void testWhisper() throws Exception {
		// SNIPPET START whisper-usage.example
		CountDownLatch latch = new CountDownLatch(1);

		String MOVIE = "media/jfk.webm";
		String LANG = "en";
		String MODEL_PATH = "models/ggml-large-v3-turbo.bin";

		Whisper whisper = Whisper.create(MODEL_PATH);
		whisper.transcribe(MOVIE, LANG).subscribe(new Flow.Subscriber<>() {

			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
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

		// SNIPPET END whisper-usage.example
	}

	@Test
	public void testTranscribeEndpoint() throws Exception {
		// SNIPPET START transcribe-client-usage.example
		String MOVIE = "media/jfk.webm";

		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
			.setBaseURL("http://localhost:8000/v1").build();

		HttpResponse<JsonObject> response = client.transcribe(MOVIE);
		System.out.println(response.statusCode());
		System.out.println(response.body().encodePrettily());
		// SNIPPET END transcribe-client-usage.example
	}

	@Test
	public void testRealtimeEndpoint() throws Exception {
		// SNIPPET START realtime-client-usage.example
		String MOVIE = "media/jfk.webm";

		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_VOXTRAL_MODEL_NAME)
			.setBaseURL("http://localhost:8000/v1").build();

		client.realtime(MOVIE);
		// SNIPPET END realtime-client-usage.example
		System.in.read();
	}
}
