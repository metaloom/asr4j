package io.metaloom.asr.whisper;

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

				boolean isSilence = isSilentRMS(chunk, 0.01f);
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

						// Silence was broken so flush the chunks out
						audioChunkConsumer.accept(new PCMAudioChunk(concat(chunks, totalSamples)));
						chunks.clear();
						totalSamples = 0;
						
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

	private static short[] floatToPCM16(float[] input) {
		short[] out = new short[input.length];
		for (int i = 0; i < input.length; i++) {
			float v = Math.max(-1f, Math.min(1f, input[i]));
			out[i] = (short) (v * 32767);
		}
		return out;
	}

	private static boolean isSilentRMS(float[] samples, float threshold) {
		double sum = 0;
		for (float s : samples) {
			sum += s * s;
		}
		double rms = Math.sqrt(sum / samples.length);
		return rms < threshold;
	}

	// 0.02
	private static float[] concat(List<float[]> chunks, int totalSamples) {
		float[] pcm = new float[totalSamples];
		int offset = 0;
		for (float[] c : chunks) {
			System.arraycopy(c, 0, pcm, offset, c.length);
			offset += c.length;
		}
		return pcm;
	}

	private static boolean isSilent(float[] samples, float threshold) {
		float max = 0;
		for (float s : samples) {
			max = Math.max(max, Math.abs(s));
		}
		return max < threshold;
	}
}
