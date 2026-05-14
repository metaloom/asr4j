package io.metaloom.asr.client;

import io.vertx.core.json.JsonObject;

/**
 * A transcript segment emitted by the realtime ASR pipeline.
 *
 * <p>
 * A segment can either be an incremental delta produced while the model is
 * still transcribing a chunk of audio, or a final segment that signals that the
 * server is done with the current audio chunk. The raw vLLM event payload is
 * exposed via {@link #raw()} to allow callers to access additional metadata
 * (timing, token information, ...).
 * </p>
 */
public class ASRSegment {

	private final String text;
	private final boolean finalSegment;
	private final String eventType;
	private final JsonObject raw;

	public ASRSegment(String text, boolean finalSegment, String eventType, JsonObject raw) {
		this.text = text;
		this.finalSegment = finalSegment;
		this.eventType = eventType;
		this.raw = raw;
	}

	/**
	 * The text of this segment. For delta events this is only the new piece that
	 * was added to the transcript. For final events this is the full text of the
	 * just-completed audio chunk.
	 */
	public String text() {
		return text;
	}

	/**
	 * {@code true} if this segment marks the end of a transcription chunk
	 * (server signalled "done"). The text may be empty in that case.
	 */
	public boolean isFinal() {
		return finalSegment;
	}

	/**
	 * The vLLM/realtime event type that produced this segment
	 * (e.g. {@code transcription.delta}, {@code response.audio_transcript.done}).
	 */
	public String eventType() {
		return eventType;
	}

	/**
	 * The raw vLLM event payload as parsed JSON.
	 */
	public JsonObject raw() {
		return raw;
	}

	@Override
	public String toString() {
		return "ASRSegment{" + (finalSegment ? "FINAL" : "delta") + " type=" + eventType + " text=\"" + text + "\"}";
	}
}
