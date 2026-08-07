package io.metaloom.asr.whisper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts and pre-loads the native shared libraries that libwhisper.so depends on.
 * Libraries must be loaded in dependency order before JNA loads libwhisper.
 */
public class WhisperNativeLoader {

	private static final Logger logger = LoggerFactory.getLogger(WhisperNativeLoader.class);

	private static boolean loaded = false;

	private static boolean cudaPreloaded = false;

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

	/** "NVRM version: NVIDIA UNIX x86_64 Kernel Module  595.71.05  ..." */
	private static final Pattern NVRM_VERSION = Pattern.compile("Kernel Module\\s+([0-9]+(?:\\.[0-9]+)+)");

	/** Where distributions keep the driver's userspace libcuda, most-canonical first. */
	private static final String[] LIBCUDA_DIRS = {
		"/usr/lib/x86_64-linux-gnu",
		"/usr/lib64",
		"/usr/lib"
	};

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		preloadCudaDriver();
		for (String lib : LIBS) {
			boolean optional = isOptional(lib);
			extractAndLoad(lib, optional);
		}
		loaded = true;
	}

	/**
	 * Load the CUDA driver library that matches the running kernel driver, before libggml-cuda is loaded.
	 *
	 * <p>
	 * libggml-cuda carries a RUNPATH that can point at a distribution directory holding a <em>stale</em>
	 * libcuda.so.1 from a previously installed driver. The CUDA runtime then reports
	 * {@code cudaErrorInsufficientDriver} and ggml silently falls back to CPU - transcription still succeeds,
	 * just an order of magnitude slower, so nothing fails visibly.
	 *
	 * <p>
	 * {@link System#load(String)} registers the library under its SONAME, so loading the correct libcuda first
	 * means the later DT_NEEDED lookup resolves to it and the RUNPATH copy is never consulted. Set
	 * {@code asr4j.libcuda} (or {@code ASR4J_LIBCUDA}) to override the auto-detected path.
	 */
	public static synchronized void preloadCudaDriver() {
		if (cudaPreloaded) {
			return;
		}
		cudaPreloaded = true;
		String explicit = System.getProperty("asr4j.libcuda", System.getenv("ASR4J_LIBCUDA"));
		if (explicit != null && !explicit.isBlank()) {
			loadCudaDriver(new File(explicit));
			return;
		}
		String version = runningDriverVersion();
		if (version == null) {
			logger.debug("No NVIDIA kernel driver detected - leaving CUDA driver resolution to the linker");
			return;
		}
		for (String dir : LIBCUDA_DIRS) {
			File candidate = new File(dir, "libcuda.so." + version);
			if (candidate.isFile()) {
				loadCudaDriver(candidate);
				return;
			}
		}
		logger.debug("Found no libcuda.so.{} matching the running driver - leaving resolution to the linker", version);
	}

	private static void loadCudaDriver(File lib) {
		try {
			System.load(lib.getAbsolutePath());
			logger.debug("Pre-loaded CUDA driver library {}", lib);
		} catch (Throwable e) {
			// A missing or unloadable driver library is not fatal: ggml falls back to CPU.
			logger.debug("Could not pre-load CUDA driver library {} - {}", lib, e.getMessage());
		}
	}

	/**
	 * @return the version string reported by the loaded NVIDIA kernel module, or null when there is none
	 */
	private static String runningDriverVersion() {
		File proc = new File("/proc/driver/nvidia/version");
		if (!proc.isFile()) {
			return null;
		}
		try {
			for (String line : java.nio.file.Files.readAllLines(proc.toPath())) {
				Matcher m = NVRM_VERSION.matcher(line);
				if (m.find()) {
					return m.group(1);
				}
			}
		} catch (Exception e) {
			logger.debug("Could not read {} - {}", proc, e.getMessage());
		}
		return null;
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
