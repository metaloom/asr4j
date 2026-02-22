package io.metaloom.asr.whisper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

import io.github.ggerganov.whispercpp.bean.WhisperSegment;

public class WhisperTest {

	private static final String MOVIE = "media/jfk.webm";
	private static final String LANG = "en";
	private static final String MODEL_PATH = "models/ggml-large-v3-turbo.bin";

	@Test
	public void testWhisper() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);

		Whisper whisper = Whisper.create(MODEL_PATH);
		final List<WhisperSegment> segments = new ArrayList<>();
		whisper.transcribe(MOVIE, LANG).subscribe(new Flow.Subscriber<>() {

			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscription.request(Long.MAX_VALUE); // request all items
			}

			@Override
			public void onNext(WhisperSegment segment) {
				segments.add(segment);
				System.out.println("start: " + segment.getStart());
				System.out.println("end: " + segment.getEnd());
				System.out.println("text: " + segment.getSentence());
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

		segments.sort((a, b) -> Long.compare(a.getStart(), b.getStart()));

		for (WhisperSegment segment : segments) {
			System.out.print(segment.getSentence());
		}

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
