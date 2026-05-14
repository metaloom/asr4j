package io.metaloom.asr.client;

import java.io.File;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ASRClientTest {

	public static final String MOVIE = "media/jfk.webm";
	private static final String TEST_BASE_URL = "http://localhost:8000/v1";

	@Test
	public void testClient() throws Exception {
		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
			.setBaseURL(TEST_BASE_URL).build();
		HttpResponse<JsonObject> response = client.transcribe(MOVIE);
		System.out.println(response.statusCode());
		System.out.println(response.body().encodePrettily());
	}

	@Test
	public void testSegmented() throws Exception {
		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
			.setBaseURL(TEST_BASE_URL).build();

		JsonArray array = client.transcribeSegmented(MOVIE);
		System.out.println(array.encodePrettily());
	}

	@Test
	public void testRealtimeEndpoint() throws Exception {
		String realtimeAudio = "media/jfk_10s_pcm16.wav";
		Assumptions.assumeTrue(new File(realtimeAudio).exists(), "Missing realtime fixture: " + realtimeAudio);

		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_VOXTRAL_MINI_4B_RT_MODEL_NAME)
			.setBaseURL(TEST_BASE_URL).build();
		client.realtime(realtimeAudio);
	}

	@Test
	public void testRealtimePulseEndpoint() throws Exception {
		Assumptions.assumeTrue(isPactlReachable(), "PulseAudio tools are not available.");

		String pulseSource = System.getenv().getOrDefault("ASR_PULSE_SOURCE", "default");
		String configuredChunkWindow = System.getenv().getOrDefault(
			"ASR_PULSE_MAX_CHUNK_SECONDS",
			System.getenv().getOrDefault("ASR_PULSE_DURATION_SECONDS", "3"));
		int maxChunkSeconds = Integer.parseInt(configuredChunkWindow);

		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_VOXTRAL_MINI_4B_RT_MODEL_NAME)
			.setBaseURL(TEST_BASE_URL).build();
		client.realtimePulse(pulseSource, maxChunkSeconds);
	}

	private static boolean isPactlReachable() {
		try {
			Process process = new ProcessBuilder("pactl", "info")
				.redirectErrorStream(true)
				.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return false;
			}
			if (process.exitValue() != 0) {
				return false;
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			return output.contains("Server Name") || output.contains("PulseAudio");
		} catch (Exception e) {
			return false;
		}
	}
}
