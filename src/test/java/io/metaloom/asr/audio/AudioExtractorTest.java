package io.metaloom.asr.audio;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import io.metaloom.asr.whisper.AudioExtractor;

public class AudioExtractorTest {

	public static final String MOVIE = "media/jfk.webm";

	@Test
	public void testExtract() throws Exception {
		AtomicLong l = new AtomicLong();
		AudioExtractor.decodeAudioToWAV(MOVIE, chunk -> {
			byte[] wavData = chunk.getAudio();
			try {
				File file = new File("out", "sample_" + l.incrementAndGet() + ".wav");
				System.out.println("Writing " + file);
				FileUtils.writeByteArrayToFile(file, wavData);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
}
