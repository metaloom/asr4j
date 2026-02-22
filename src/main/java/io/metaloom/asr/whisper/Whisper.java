package io.metaloom.asr.whisper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

import io.github.ggerganov.whispercpp.WhisperCpp;
import io.github.ggerganov.whispercpp.bean.WhisperSegment;
import io.github.ggerganov.whispercpp.params.WhisperFullParams;
import io.github.ggerganov.whispercpp.params.WhisperSamplingStrategy;

public class Whisper {

	private WhisperCpp whisperCpp;

	public Whisper(String modelPath) throws FileNotFoundException {
		whisperCpp = new WhisperCpp();
		whisperCpp.initContext(modelPath);
	}

	public Flow.Publisher<WhisperSegment> transcribe(String filePath, String lang) throws Exception {
		SubmissionPublisher<WhisperSegment> publisher = new SubmissionPublisher<>();

		new Thread(() -> {

			try {
				WhisperFullParams.ByValue whisperParams = whisperCpp.getFullDefaultParams(
					WhisperSamplingStrategy.WHISPER_SAMPLING_BEAM_SEARCH);

				whisperParams.temperature = 0.0f;
				whisperParams.temperature_inc = 0.2f;
				whisperParams.language = lang;

				AudioExtractor.decodeAudioToPCM(filePath, ac -> {
					try {
						List<WhisperSegment> whisperSegmentList = whisperCpp.fullTranscribeWithTime(whisperParams, ac.getAudio());

						for (WhisperSegment whisperSegment : whisperSegmentList) {
							// Publish each segment to subscribers asynchronously
							publisher.submit(whisperSegment);
						}

					} catch (Exception e) {
						publisher.closeExceptionally(e);
					}
				});
				// Close the publisher to signal completion
				publisher.close();

			} catch (Exception e) {
				e.printStackTrace();
				publisher.closeExceptionally(e);
			}
		}).start();

		return publisher;
	}

	public void transcribe(String filePath, String lang, Consumer<WhisperSegment> consumer) throws Exception {

		try {
			// By default, models are loaded from ~/.cache/whisper/ and are usually named "ggml-${name}.bin"
			// or you can provide the absolute path to the model file.
			WhisperFullParams.ByValue whisperParams = whisperCpp.getFullDefaultParams(WhisperSamplingStrategy.WHISPER_SAMPLING_BEAM_SEARCH);

			// custom configuration if required
			// whisperParams.n_threads = 8;
			whisperParams.temperature = 0.0f;
			whisperParams.temperature_inc = 0.2f;
			whisperParams.language = lang;

			AudioExtractor.decodeAudioToPCM(filePath, ac -> {
				try {
					List<WhisperSegment> whisperSegmentList = whisperCpp.fullTranscribeWithTime(whisperParams, ac.getAudio());
					for (WhisperSegment whisperSegment : whisperSegmentList) {

						consumer.accept(whisperSegment);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			whisperCpp.close();
		}

	}

	public static Whisper create(String modelPath) throws FileNotFoundException {
		return new Whisper(modelPath);
	}

}
