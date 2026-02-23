package io.metaloom.asr.client.impl;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.metaloom.asr.client.ASRClient;
import io.metaloom.asr.whisper.AudioExtractor;
import io.metaloom.asr.whisper.PCMAudioChunk;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ASRClientImpl implements ASRClient {

	private static Logger logger = LoggerFactory.getLogger(ASRClientImpl.class);

	private final String baseURL;

	private String model;

	private String lang;

	private HttpClient client;

	// public static final String MODEL = "mistralai/Voxtral-Mini-4B-Realtime-2602";
	public static final String MODEL = "openai/whisper-large-v3";

	public static final String MOVIE = "media/jfk.webm";

	public static int deltaCount = 0;

	protected ASRClientImpl(ASRClient.Builder builder) {
		this.baseURL = builder.baseURL();
		this.model = builder.model();
		this.lang = "en";
		this.client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	}

	@Override
	public HttpResponse<JsonObject> transcribe(String mediaPath) throws Exception {

		byte[] wavData = AudioExtractor.decodeAudioToWAV(mediaPath);
		logger.info("WAV bytes: {}", wavData.length);

		String boundary = "-------" + UUID.randomUUID();
		byte[] body = buildMultipartBody(wavData, boundary);

		URI uri = URI.create(baseURL + "/audio/transcriptions");
		System.out.println(uri);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(uri)
			.header("Accept", "*/*")
			.header("Authorization", "Bearer EMPTY")
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();

		BodyHandler<JsonObject> jsonHandler = responseInfo -> BodySubscribers.mapping(
			BodySubscribers.ofString(StandardCharsets.UTF_8),
			JsonObject::new);

		HttpResponse<JsonObject> response = client.send(request, jsonHandler);

		return response;
	}

	@Override
	public JsonArray transcribeSegmented(String mediaPath) throws Exception {

		JsonArray json = new JsonArray();
		AudioExtractor.decodeAudioToWAV(mediaPath, chunk -> {
			try {
				byte[] wavData = chunk.getAudio();
				logger.info("WAV bytes: {}", wavData.length);

				String boundary = "-------" + UUID.randomUUID();
				byte[] body = buildMultipartBody(wavData, boundary);

				URI uri = URI.create(baseURL + "/audio/transcriptions");
				System.out.println(uri);
				HttpRequest request = HttpRequest.newBuilder()
					.uri(uri)
					.header("Accept", "*/*")
					.header("Authorization", "Bearer EMPTY")
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArray(body))
					.build();

				BodyHandler<JsonObject> jsonHandler = responseInfo -> BodySubscribers.mapping(
					BodySubscribers.ofString(StandardCharsets.UTF_8),
					JsonObject::new);

				HttpResponse<JsonObject> response = client.send(request, jsonHandler);
				//System.out.println(response.body());
				json.add(response.body().getString("text"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return json;
	}

	private byte[] buildMultipartBody(byte[] wavData, String boundary) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String contentType = "audio/wav";
		String CRLF = "\r\n";
		boundary = "--" + boundary;
		// model field
		out.write((boundary + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"model\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write((model).getBytes(StandardCharsets.UTF_8));
		out.write((CRLF).getBytes(StandardCharsets.UTF_8));

		// lang field
		out.write((boundary + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"language\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write((lang).getBytes(StandardCharsets.UTF_8));
		out.write((CRLF).getBytes(StandardCharsets.UTF_8));

		// file field (must be "file")
		out.write((boundary + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"out.wav\"" + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
		out.write(wavData);
		out.write((CRLF).getBytes(StandardCharsets.UTF_8));

		// closing boundary
		out.write((boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	@Override
	public void realtime(String filename) throws Exception {

		URI uri = new URI(baseURL.replace("http", "ws") + "/realtime");
		// Queue to store incoming messages
		LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

		// Create WebSocket
		WebSocket webSocket = client.newWebSocketBuilder()
			.buildAsync(uri, new Listener() {

				@Override
				public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
					messageQueue.offer(data.toString()); // enqueue incoming message
					return Listener.super.onText(webSocket, data, last);
				}

			}).join();

		// Sequentially send messages and await reply
		sendAndAwait(webSocket, messageQueue, sessionUpdate());
		sendAndAwait(webSocket, messageQueue, bufferCommit(false));

		AtomicLong sendMsg = new AtomicLong(0);
		AudioExtractor.decodeAudioToPCM(filename, ac -> {
			String data = base64Encode2(ac);
			try {
				Thread.sleep(20);
				sendMsg.incrementAndGet();
				sendAndAwait(webSocket, messageQueue, pcmData(data));
				sendAndAwait(webSocket, messageQueue, bufferCommit(false));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		sendAndAwait(webSocket, messageQueue, bufferCommit(true));

		webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye").join();
	}

	private JsonObject bufferCommit(boolean finalFlag) {
		JsonObject msg = new JsonObject();
		msg.put("type", "input_audio_buffer.commit");
		msg.put("final", finalFlag);
		return msg;
	}

	private JsonObject sessionUpdate() {
		JsonObject msg = new JsonObject();
		msg.put("type", "session.update");
		msg.put("model", MODEL);
		return msg;
	}

	private JsonObject pcmData(String data) {

		JsonObject msg3 = new JsonObject();
		msg3.put("type", "input_audio_buffer.append");
		msg3.put("audio", data);
		return msg3;
	}

	private static void sendAndAwait(WebSocket webSocket, LinkedBlockingQueue<String> queue, JsonObject json) throws Exception {
		// System.out.println("Sending: " + json.getString("type"));
		webSocket.sendText(json.encode(), true).join(); // send message

		// Wait for reply
		String reply = queue.poll(5, TimeUnit.SECONDS);
		if (reply != null) {
			try {

				JsonObject replyJson = new JsonObject(reply);
				if ("transcription.delta".equals(replyJson.getString("type"))) {
					System.out.print(replyJson.getString("delta"));
					deltaCount++;
					if (deltaCount % 80 == 0) {
						System.out.println();
					}
				}
			} catch (Exception e) {
				System.err.println(reply);
			}
		} else {
			System.out.println("No reply received for message: " + json.getString("type"));
		}

	}

	protected String base64Encode(PCMAudioChunk ac) {
		float[] data = ac.getAudio();

		// Step 1: Convert float[] to byte[]
		ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4); // 4 bytes per float
		for (float f : data) {
			byteBuffer.putFloat(f);
		}
		byte[] bytes = byteBuffer.array();

		String base64String = Base64.getEncoder().encodeToString(bytes);
		return base64String;
	}

	protected String base64Encode2(PCMAudioChunk ac) {
		float[] data = ac.getAudio();
		byte[] pcm16 = new byte[data.length * 2]; // 2 bytes per sample

		for (int i = 0; i < data.length; i++) {
			// Clip to [-1,1] just in case
			float f = Math.max(-1f, Math.min(1f, data[i]));
			short s = (short) (f * 32767); // Convert float -> PCM16
			// Little-endian
			pcm16[i * 2] = (byte) (s & 0xFF);
			pcm16[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
		}

		return Base64.getEncoder().encodeToString(pcm16);
	}

}
