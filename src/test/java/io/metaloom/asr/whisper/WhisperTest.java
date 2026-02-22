package io.metaloom.asr.whisper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

import io.github.ggerganov.whispercpp.bean.WhisperSegment;
import io.metaloom.asr.whisper.Whisper;

public class WhisperTest {

	private static final String MOVIE = "movies/siw.das.fehlende.fragment.avi";
	private static final String LANG = "de";
	private static final String MODEL_PATH = "models/ggml-large-v3-turbo.bin";

	@Test
	public void testWhisper() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);

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
	}

	@Test
	public void testConsumer() throws Exception {
		Whisper whisper = Whisper.create(MODEL_PATH);
		whisper.transcribe(MOVIE, LANG, seg -> {

			long start = seg.getStart();
			long end = seg.getEnd();
			String text = seg.getSentence();
			System.out.println("start: " + start);
			System.out.println("end: " + end);
			System.out.println("text: " + text);
		});

	}
}
