package io.metaloom.asr.whisper;

import java.util.List;

public final class PCMUtils {

	private PCMUtils() {
	}

	public static boolean isSilentRMS(float[] samples, float threshold) {
		if (samples == null || samples.length == 0) {
			return true;
		}
		double sum = 0.0;
		for (float s : samples) {
			sum += s * s;
		}
		double rms = Math.sqrt(sum / samples.length);
		return rms < threshold;
	}

	public static boolean isSilentRMS(byte[] pcm16, float threshold) {
		if (pcm16 == null || pcm16.length < 2) {
			return true;
		}
		double sum = 0.0;
		int samples = pcm16.length / 2;
		for (int i = 0; i < samples; i++) {
			short s = littleEndianSignedShort(pcm16, i * 2);
			double normalized = s / 32768.0;
			sum += normalized * normalized;
		}
		double rms = Math.sqrt(sum / samples);
		return rms < threshold;
	}

	public static short littleEndianSignedShort(byte[] data, int offset) {
		int lo = data[offset] & 0xFF;
		int hi = data[offset + 1];
		return (short) ((hi << 8) | lo);
	}

	public static short[] floatToPCM16(float[] input) {
		short[] out = new short[input.length];
		for (int i = 0; i < input.length; i++) {
			float v = Math.max(-1f, Math.min(1f, input[i]));
			out[i] = (short) (v * 32767);
		}
		return out;
	}

	// 0.02
	public static float[] concat(List<float[]> chunks, int totalSamples) {
		float[] pcm = new float[totalSamples];
		int offset = 0;
		for (float[] c : chunks) {
			System.arraycopy(c, 0, pcm, offset, c.length);
			offset += c.length;
		}
		return pcm;
	}

	public static boolean isSilent(float[] samples, float threshold) {
		float max = 0;
		for (float s : samples) {
			max = Math.max(max, Math.abs(s));
		}
		return max < threshold;
	}
}