package io.metaloom.asr.client.impl;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.metaloom.asr.client.ASRClient;

public class ASRClientBuilderImpl implements ASRClient.Builder {

	private static final Logger logger = LoggerFactory.getLogger(ASRClientBuilderImpl.class);

	private String baseURL;

	private String model;

	@Override
	public ASRClient build() {
		Objects.requireNonNull(baseURL, "You must specify the baseURL for the client.");
		Objects.requireNonNull(model, "You must specify the model for the client.");
		return new ASRClientImpl(this);
	}

	@Override
	public String baseURL() {
		return baseURL;
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public ASRClient.Builder setBaseURL(String baseURL) {
		if (baseURL != null && baseURL.endsWith("/")) {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		this.baseURL = baseURL;
		return this;
	}

	@Override
	public ASRClient.Builder setModel(String model) {
		this.model = model;
		return this;
	}

}
