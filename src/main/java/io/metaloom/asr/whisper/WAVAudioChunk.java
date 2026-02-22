package io.metaloom.asr.whisper;

public class WAVAudioChunk {

	private byte[] audio;

	public WAVAudioChunk(byte[] audio) {
		this.audio = audio;
	}

	public byte[] getAudio() {
		return audio;
	}
}
