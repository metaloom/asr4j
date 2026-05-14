package io.metaloom.asr.whisper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts and pre-loads the native shared libraries that libwhisper.so depends on.
 * Libraries must be loaded in dependency order before JNA loads libwhisper.
 */
public class WhisperNativeLoader {

	private static final Logger logger = LoggerFactory.getLogger(WhisperNativeLoader.class);

	private static boolean loaded = false;

	/**
	 * Native libs in load order (dependencies first).
	 * The versioned names must match the filenames in the whispercpp jar.
	 */
	private static final String[] LIBS = {
		"libggml-base.so.0.9.5",
		"libggml-cpu.so.0.9.5",
		"libggml-cuda.so.0.9.5",
		"libggml.so.0.9.5",
		"libwhisper.so"
	};

	private static final String[] OPTIONAL_LIBS = {
		"libggml-cuda.so.0.9.5"
	};

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		for (String lib : LIBS) {
			boolean optional = isOptional(lib);
			extractAndLoad(lib, optional);
		}
		loaded = true;
	}

	private static boolean isOptional(String lib) {
		for (String opt : OPTIONAL_LIBS) {
			if (opt.equals(lib)) {
				return true;
			}
		}
		return false;
	}

	private static void extractAndLoad(String name, boolean optional) {
		try (InputStream inputStream = WhisperNativeLoader.class.getClassLoader().getResourceAsStream(name)) {
			if (inputStream == null) {
				if (optional) {
					logger.debug("Optional native lib not found in classpath: {}", name);
					return;
				}
				throw new RuntimeException("Native lib not found in classpath: " + name);
			}
			File tempFile = File.createTempFile(name, ".so");
			tempFile.deleteOnExit();
			try (OutputStream out = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
			System.load(tempFile.getAbsolutePath());
			logger.debug("Loaded native lib: {} from {}", name, tempFile.getAbsolutePath());
		} catch (Exception e) {
			if (optional) {
				logger.debug("Failed to load optional native lib: {} - {}", name, e.getMessage());
			} else {
				throw new RuntimeException("Failed to load native library: " + name, e);
			}
		}
	}
}
