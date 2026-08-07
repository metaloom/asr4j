package io.metaloom.asr.whisper;

import static io.metaloom.asr.whisper.PCMUtils.concat;
import static io.metaloom.asr.whisper.PCMUtils.floatToPCM16;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;

import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class AudioExtractor {

	public static byte[] decodeAudioToWAV(String videoPath) throws Exception {

		try (
			FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath);
			ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {

			grabber.setAudioChannels(1);
			grabber.setSampleRate(16000);
			grabber.setSampleFormat(AV_SAMPLE_FMT_FLT);
			grabber.start();

			try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(wavOut, 1)) {
				recorder.setFormat("wav");
				recorder.setAudioChannels(1);
				recorder.setSampleRate(16000);
				recorder.setSampleFormat(AV_SAMPLE_FMT_S16);
				recorder.setAudioCodec(AV_CODEC_ID_PCM_S16LE);
				recorder.start();

				Frame frame;
				while ((frame = grabber.grabSamples()) != null) {
					if (frame.samples == null) {
						continue;
					}

					FloatBuffer fb = (FloatBuffer) frame.samples[0];
					float[] floats = new float[fb.remaining()];
					fb.get(floats);

					short[] pcm16 = floatToPCM16(floats);
					ShortBuffer sb = ShortBuffer.wrap(pcm16);
					recorder.recordSamples(16000, 1, sb);
				}

			}

			return wavOut.toByteArray();
		}
	}

	public static void decodeAudioToWAV(String videoPath, Consumer<WAVAudioChunk> audioChunkConsumer) throws Exception {
		decodeAudioToPCM(videoPath, chunk -> {
			ByteArrayOutputStream wavOut = new ByteArrayOutputStream();

			try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(wavOut, 1)) {
				recorder.setFormat("wav");
				recorder.setAudioChannels(1);
				recorder.setSampleRate(16000);
				recorder.setSampleFormat(AV_SAMPLE_FMT_S16);
				recorder.setAudioCodec(AV_CODEC_ID_PCM_S16LE);
				recorder.start();
				short[] pcm16 = floatToPCM16(chunk.getAudio());
				ShortBuffer sb = ShortBuffer.wrap(pcm16);
				recorder.recordSamples(16000, 1, sb);
			} catch (Exception e) {
				e.printStackTrace();
			}
			audioChunkConsumer.accept(new WAVAudioChunk(wavOut.toByteArray()));
		});
	}

	public static void decodeAudioToPCM(String videoPath, Consumer<PCMAudioChunk> audioChunkConsumer) throws Exception {

		try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {

			grabber.setAudioChannels(1);
			grabber.setSampleRate(16000);
			grabber.setSampleFormat(AV_SAMPLE_FMT_FLT);

			grabber.start();

			List<float[]> chunks = new ArrayList<>();
			List<float[]> silenceChunks = new ArrayList<>();
			int totalSamples = 0;
			int silenceCounter = 0;
			int silenceMinSamples = 3000;
			Frame frame;
			while ((frame = grabber.grabSamples()) != null) {
				if (frame.samples == null) {
					continue;
				}

				long ts = frame.timestamp;
				FloatBuffer fb = (FloatBuffer) frame.samples[0];
				float[] chunk = new float[fb.remaining()];
				fb.get(chunk);

				boolean isSilence = PCMUtils.isSilentRMS(chunk, 0.01f);
				if (isSilence) {
					silenceCounter += chunk.length;
					silenceChunks.add(chunk);
				} else {
					// We encountered non-silence
					// Check if the silence was long enough
					if (silenceCounter > silenceMinSamples) {
						// Discard the silence
						silenceChunks.clear();
						silenceCounter = 0;

						// Silence was broken so flush the chunks out - but only if there is anything
						// to flush. A recording that *opens* with silence reaches this branch with
						// nothing accumulated yet, and the consumer would then be handed a
						// zero-length buffer. Whisper answers that with "offset 0ms is past the end
						// of the audio", fails to detect a language, and the whole transcription
						// aborts - for a file whose audio is perfectly fine from the second chunk on.
						if (!chunks.isEmpty()) {
							audioChunkConsumer.accept(new PCMAudioChunk(concat(chunks, totalSamples)));
							chunks.clear();
							totalSamples = 0;
						}

					} else {
						// Silence was not long enough so we add it to the regular chunks
						chunks.addAll(silenceChunks);
						totalSamples += silenceCounter;
						silenceChunks.clear();
						silenceCounter=0;
					}

					// Add the non-silence to the chunk list
					chunks.add(chunk);
					totalSamples += chunk.length;
				}

				

			}
			// Also handle remaining chunks
			if (!chunks.isEmpty()) {
				audioChunkConsumer.accept(new PCMAudioChunk(concat(chunks, totalSamples)));
				chunks.clear();
				totalSamples = 0;
			}

			// grabber.stop();

		}
	}

}
