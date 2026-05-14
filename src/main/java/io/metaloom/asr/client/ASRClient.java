package io.metaloom.asr.client;

import java.net.http.HttpResponse;
import java.util.function.Consumer;

import io.metaloom.asr.client.impl.ASRClientBuilderImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface ASRClient {

	public static final String DEFAULT_BASEURL = "http://localhost:8000";

	public static final String DEFAULT_WHISPER_MODEL_NAME = "openai/whisper-large-v3";

	public static final String DEFAULT_VOXTRAL_MINI_4B_RT_MODEL_NAME = "mistralai/Voxtral-Mini-4B-Realtime-2602";

	public static ASRClient newFaceDetectClient() {
		return newBuilder().setBaseURL(DEFAULT_BASEURL).build();
	}

	/**
	 * Create a new builder to construct a new client.
	 *
	 * @return
	 */
	public static Builder newBuilder() {
		return new ASRClientBuilderImpl();
	}

	public interface Builder {

		/**
		 * Construct a new client
		 *
		 * @return Constructed client
		 */
		ASRClient build();

		/**
		 * Return the base URL of the client.
		 *
		 * @return
		 */
		String baseURL();

		/**
		 * Set the base URL for the client.
		 *
		 * @param baseURL
		 * @return Fluent API
		 */
		Builder setBaseURL(String baseURL);

		/**
		 * Set the model for the client.
		 * 
		 * @param model
		 * @return
		 */
		Builder setModel(String model);

		/**
		 * Model name.
		 * 
		 * @return
		 */
		String model();

	}

	/**
	 * Transcribe the given media and return the transcript.
	 * 
	 * @param mediaPath
	 * @return response
	 * @throws Exception
	 */
	HttpResponse<JsonObject> transcribe(String mediaPath) throws Exception;

	/**
	 * Transcribe the media.
	 * 
	 * @param mediaPath
	 * @throws Exception
	 */
	void realtime(String mediaPath) throws Exception;

	/**
	 * Capture audio continuously from a PulseAudio source and transcribe it via
	 * the realtime API. The provided consumer receives one
	 * {@link ASRSegment} per realtime event (delta or final). The call blocks
	 * until the current thread is interrupted.
	 *
	 * @param pulseSource
	 *            PulseAudio source name (e.g. "default")
	 * @param maxChunkSeconds
	 *            Maximum chunk window in seconds before a forced commit is
	 *            performed regardless of silence detection.
	 * @param consumer
	 *            Receives transcript segments as they are produced.
	 * @throws Exception
	 */
	void realtimePulse(String pulseSource, int maxChunkSeconds, Consumer<ASRSegment> consumer) throws Exception;

	JsonArray transcribeSegmented(String movie) throws Exception;

}
