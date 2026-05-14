package io.metaloom.asr.client.impl;

import java.io.ByteArrayOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import io.metaloom.asr.client.ASRClient;
import io.metaloom.asr.whisper.AudioExtractor;
import io.metaloom.asr.whisper.PCMUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ASRClientImpl implements ASRClient {

	private static Logger logger = LoggerFactory.getLogger(ASRClientImpl.class);

	private final String baseURL;

	private String model;

	private String lang;

	private HttpClient client;

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
		byte[] wavData = filename.toLowerCase().endsWith(".wav")
			? Files.readAllBytes(Path.of(filename))
			: AudioExtractor.decodeAudioToWAV(filename);
		byte[] pcm16 = extractPcm16Payload(wavData);
		realtimePcm16(pcm16, 0L);
	}

	@Override
	public void realtimePulse(String pulseSource, int durationSeconds) throws Exception {
		if (durationSeconds <= 0) {
			throw new IllegalArgumentException("durationSeconds must be > 0");
		}
		String source = (pulseSource == null || pulseSource.isBlank()) ? "default" : pulseSource;
		int maxChunkSeconds = durationSeconds;
		int maxChunkBytes = maxChunkSeconds * 16000 * 2;
		int minChunkBytes = 16000 * 2 / 2; // 500ms minimum to avoid too small commits.
		long silenceBoundaryNs = TimeUnit.MILLISECONDS.toNanos(650);

		LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
		WebSocket webSocket = openRealtimeSocket(messageQueue);
		send(webSocket, sessionUpdate());

		ByteArrayOutputStream segmentBuffer = new ByteArrayOutputStream(maxChunkBytes);
		boolean speechDetected = false;
		long lastSpeechTs = System.nanoTime();

		try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source)) {
			grabber.setFormat("pulse");
			grabber.setAudioChannels(1);
			grabber.setSampleRate(16000);
			grabber.start();

			while (!Thread.currentThread().isInterrupted()) {
				Frame frame = grabber.grabSamples();
				if (frame == null || frame.samples == null || frame.samples.length == 0 || frame.samples[0] == null) {
					continue;
				}

				byte[] pcm = toPcm16Bytes(frame.samples[0]);
				if (pcm.length == 0) {
					continue;
				}

				segmentBuffer.write(pcm);
				long now = System.nanoTime();
				if (!PCMUtils.isSilentRMS(pcm, 0.012f)) {
					System.out.println("Detected voice");
					speechDetected = true;
					lastSpeechTs = now;
				}

				boolean silenceBoundaryReached = speechDetected
					&& (now - lastSpeechTs) >= silenceBoundaryNs
					&& segmentBuffer.size() >= minChunkBytes;
				boolean maxChunkReached = segmentBuffer.size() >= maxChunkBytes;

				if (!silenceBoundaryReached && !maxChunkReached) {
					continue;
				}

				if (speechDetected) {
					System.out.println("SNC");
					streamAndCommit(webSocket, segmentBuffer.toByteArray(), 0L);
					collectTranscript(messageQueue, 15, false);
				}

				segmentBuffer.reset();
				speechDetected = false;
				lastSpeechTs = now;
			}
		} finally {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye").join();
		}
	}

	private void realtimePcm16(byte[] pcm16, long pacingMs) throws Exception {
		LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
		WebSocket webSocket = openRealtimeSocket(messageQueue);

		send(webSocket, sessionUpdate());
		streamAndCommit(webSocket, pcm16, pacingMs);
		String transcript = collectTranscript(messageQueue, 45, true);

		webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye").join();
		if (!transcript.isEmpty()) {
			System.out.println();
		}
	}

	private WebSocket openRealtimeSocket(LinkedBlockingQueue<String> messageQueue) throws Exception {
		String wsURL = baseURL.startsWith("https")
			? baseURL.replaceFirst("^https", "wss")
			: baseURL.replaceFirst("^http", "ws");
		URI uri = new URI(wsURL + "/realtime");
		return client.newWebSocketBuilder()
			.buildAsync(uri, new Listener() {

				@Override
				public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
					messageQueue.offer(data.toString());
					return Listener.super.onText(webSocket, data, last);
				}

			}).join();
	}

	private void streamAndCommit(WebSocket webSocket, byte[] pcm16, long pacingMs) throws Exception {
		int bytesPerChunk = 320; // 10ms at 16kHz mono PCM16
		long effectivePacingMs = Math.max(0L, pacingMs);
		for (int offset = 0; offset < pcm16.length; offset += bytesPerChunk) {
			int end = Math.min(offset + bytesPerChunk, pcm16.length);
			byte[] chunk = Arrays.copyOfRange(pcm16, offset, end);
			String data = Base64.getEncoder().encodeToString(chunk);
			send(webSocket, pcmData(data));
			if (effectivePacingMs > 0) {
				Thread.sleep(effectivePacingMs);
			}
		}
		send(webSocket, bufferCommit());
	}

	private String collectTranscript(LinkedBlockingQueue<String> messageQueue, int timeoutSeconds, boolean requireOutput) throws Exception {
		StringBuilder transcript = new StringBuilder();
		List<String> seenEventTypes = new ArrayList<>();
		List<String> sampleDeltaPayloads = new ArrayList<>();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		long lastDelta = -1L;

		while (System.nanoTime() < deadline) {
			String reply = messageQueue.poll(2, TimeUnit.SECONDS);
			if (reply == null) {
				if (lastDelta > 0 && System.nanoTime() - lastDelta > TimeUnit.SECONDS.toNanos(2)) {
					break;
				}
				continue;
			}

			JsonObject replyJson;
			try {
				replyJson = new JsonObject(reply);
			} catch (Exception e) {
				logger.debug("Ignoring non-JSON realtime frame: {}", reply);
				continue;
			}

			String type = replyJson.getString("type");
			if (type == null) {
				type = "";
			}
			if (!type.isEmpty() && seenEventTypes.size() < 30) {
				seenEventTypes.add(type);
			}
			if ("transcription.delta".equals(type) && sampleDeltaPayloads.size() < 3) {
				sampleDeltaPayloads.add(replyJson.encode());
			}

			if ("error".equals(type) || replyJson.containsKey("error")) {
				throw new IllegalStateException("Realtime server returned error: " + replyJson.encode());
			}

			String delta = extractDelta(replyJson);
			if (delta != null && !delta.isEmpty()) {
				transcript.append(delta);
				System.out.print(delta);
				lastDelta = System.nanoTime();
			}

			String finalText = extractFinalText(replyJson);
			if (finalText != null && !finalText.isEmpty() && transcript.isEmpty()) {
				transcript.append(finalText);
				System.out.print(finalText);
				lastDelta = System.nanoTime();
			}

			if (isDoneEvent(type)) {
				break;
			}
		}

		String finalTranscript = transcript.toString().trim();
		if (requireOutput && finalTranscript.isEmpty()) {
			throw new IllegalStateException(
				"Realtime session completed without transcript output. Seen event types: " + seenEventTypes
					+ ", sample delta payloads: " + sampleDeltaPayloads);
		}
		return finalTranscript;
	}

	private static byte[] toPcm16Bytes(Buffer sampleBuffer) {
		if (sampleBuffer instanceof FloatBuffer fb) {
			FloatBuffer copy = fb.duplicate();
			byte[] pcm = new byte[copy.remaining() * 2];
			int out = 0;
			while (copy.hasRemaining()) {
				float f = Math.max(-1f, Math.min(1f, copy.get()));
				short s = (short) (f * 32767f);
				pcm[out++] = (byte) (s & 0xFF);
				pcm[out++] = (byte) ((s >> 8) & 0xFF);
			}
			return pcm;
		}
		if (sampleBuffer instanceof ShortBuffer sb) {
			ShortBuffer copy = sb.duplicate();
			byte[] pcm = new byte[copy.remaining() * 2];
			int out = 0;
			while (copy.hasRemaining()) {
				short s = copy.get();
				pcm[out++] = (byte) (s & 0xFF);
				pcm[out++] = (byte) ((s >> 8) & 0xFF);
			}
			return pcm;
		}
		if (sampleBuffer instanceof ByteBuffer bb) {
			ByteBuffer copy = bb.duplicate();
			byte[] pcm = new byte[copy.remaining()];
			copy.get(pcm);
			return pcm;
		}
		return new byte[0];
	}

	private JsonObject bufferCommit() {
		JsonObject msg = new JsonObject();
		msg.put("type", "input_audio_buffer.commit");
		return msg;
	}

	private JsonObject sessionUpdate() {
		JsonObject msg = new JsonObject();
		msg.put("type", "session.update");
		msg.put("model", model);
		return msg;
	}

	private JsonObject pcmData(String data) {

		JsonObject msg3 = new JsonObject();
		msg3.put("type", "input_audio_buffer.append");
		msg3.put("audio", data);
		return msg3;
	}

	private static void send(WebSocket webSocket, JsonObject json) {
		webSocket.sendText(json.encode(), true).join();
	}

	private static byte[] extractPcm16Payload(byte[] wavData) {
		if (wavData.length < 12 || wavData[0] != 'R' || wavData[1] != 'I' || wavData[2] != 'F' || wavData[3] != 'F') {
			throw new IllegalArgumentException("Invalid WAV data: missing RIFF header.");
		}
		int channels = 1;
		int bitsPerSample = 16;
		int audioFormat = 1;
		byte[] pcmData = null;

		int offset = 12;
		while (offset + 8 <= wavData.length) {
			String chunkId = new String(wavData, offset, 4, StandardCharsets.US_ASCII);
			long chunkSize = littleEndianUnsignedInt(wavData, offset + 4);
			long dataStart = offset + 8L;
			long dataEnd = dataStart + chunkSize;
			if (dataEnd > wavData.length || dataStart > dataEnd) {
				break;
			}
			if ("fmt ".equals(chunkId) && chunkSize >= 16) {
				int base = (int) dataStart;
				audioFormat = littleEndianUnsignedShort(wavData, base);
				channels = littleEndianUnsignedShort(wavData, base + 2);
				bitsPerSample = littleEndianUnsignedShort(wavData, base + 14);
			}
			if ("data".equals(chunkId)) {
				pcmData = Arrays.copyOfRange(wavData, (int) dataStart, (int) dataEnd);
				break;
			}
			offset = (int) (dataEnd + (chunkSize & 1L));
		}

		if (pcmData == null) {
			throw new IllegalArgumentException("Invalid WAV data: no data chunk found.");
		}
		if (audioFormat != 1 || bitsPerSample != 16) {
			throw new IllegalArgumentException("Realtime currently supports PCM16 WAV only.");
		}
		if (channels <= 1) {
			return pcmData;
		}

		int frameCount = pcmData.length / (2 * channels);
		byte[] mono = new byte[frameCount * 2];
		for (int frame = 0; frame < frameCount; frame++) {
			int sum = 0;
			for (int ch = 0; ch < channels; ch++) {
				int sampleOffset = (frame * channels + ch) * 2;
				sum += littleEndianSignedShort(pcmData, sampleOffset);
			}
			short avg = (short) (sum / channels);
			int out = frame * 2;
			mono[out] = (byte) (avg & 0xFF);
			mono[out + 1] = (byte) ((avg >> 8) & 0xFF);
		}
		return mono;
	}

	private static long littleEndianUnsignedInt(byte[] data, int offset) {
		return ((long) data[offset] & 0xFF)
			| (((long) data[offset + 1] & 0xFF) << 8)
			| (((long) data[offset + 2] & 0xFF) << 16)
			| (((long) data[offset + 3] & 0xFF) << 24);
	}

	private static int littleEndianUnsignedShort(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
	}

	private static short littleEndianSignedShort(byte[] data, int offset) {
		int lo = data[offset] & 0xFF;
		int hi = data[offset + 1];
		return (short) ((hi << 8) | lo);
	}

	private static boolean isDoneEvent(String type) {
		return "response.done".equals(type)
			|| "response.audio_transcript.done".equals(type)
			|| "transcription.done".equals(type)
			|| "conversation.item.input_audio_transcription.completed".equals(type);
	}

	private static String extractDelta(JsonObject replyJson) {
		String type = replyJson.getString("type");
		if (type == null) {
			return null;
		}
		if ("transcription.delta".equals(type)
			|| "response.audio_transcript.delta".equals(type)
			|| "response.output_text.delta".equals(type)
			|| "conversation.item.input_audio_transcription.delta".equals(type)) {
			if (replyJson.containsKey("delta")) {
				return valueToText(replyJson.getValue("delta"));
			}
			if (replyJson.containsKey("text")) {
				return valueToText(replyJson.getValue("text"));
			}
		}
		return null;
	}

	private static String extractFinalText(JsonObject replyJson) {
		String type = replyJson.getString("type");
		if (type == null) {
			return null;
		}
		if ("transcription.done".equals(type) || "response.audio_transcript.done".equals(type)) {
			if (replyJson.containsKey("text")) {
				return valueToText(replyJson.getValue("text"));
			}
			if (replyJson.containsKey("transcript")) {
				return valueToText(replyJson.getValue("transcript"));
			}
		}
		if ("conversation.item.input_audio_transcription.completed".equals(type)
			&& replyJson.containsKey("transcript")) {
			return valueToText(replyJson.getValue("transcript"));
		}
		return null;
	}

	private static String valueToText(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof JsonObject obj) {
			if (obj.containsKey("text")) {
				return valueToText(obj.getValue("text"));
			}
			if (obj.containsKey("content")) {
				return valueToText(obj.getValue("content"));
			}
		}
		if (value instanceof JsonArray arr && !arr.isEmpty()) {
			Object first = arr.getValue(0);
			return valueToText(first);
		}
		return value.toString();
	}

}
