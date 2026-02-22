package io.metaloom.asr.client;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class ASRClientTest {

	public static final String MOVIE = "movies/short_siw.m4v";

	@Test
	public void testClient() throws Exception {
		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
			.setBaseURL("http://plexus:8000/v1").build();
		HttpResponse<JsonObject> response = client.transcribe(MOVIE);
		System.out.println(response.statusCode());
		System.out.println(response.body().encodePrettily());
	}

	@Test
	public void testRealtimeEndpoint() throws Exception {
		ASRClient client = ASRClient.newBuilder()
			.setModel(ASRClient.DEFAULT_WHISPER_MODEL_NAME)
			.setBaseURL("http://plexus:8000/v1").build();
		client.realtime(MOVIE);
	}
}
