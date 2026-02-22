package io.metaloom.asr.whisper;

public class PCMAudioChunk {

	private float[] audio;

	public PCMAudioChunk(float[] audio) {
		this.audio = audio;
	}

	public float[] getAudio() {
		return audio;
	}
}
