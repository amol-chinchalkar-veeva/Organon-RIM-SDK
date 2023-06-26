/*
 * --------------------------------------------------------------------
 * UDC:         VpsAPIResponse
 * Author:      achinchalkar @ Veeva
 * Date:        2019-07-25
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.util.api;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.math.BigDecimal;

@UserDefinedClassInfo()
public class VpsAPIResponse {

	private static final String APIFIELD_ERRORS = "errors";
	private static final String APIFIELD_RESPONSE_DETAILS = "responseDetails";
	private static final String APIFIELD_RESPONSE_MESSAGE = "responseMessage";
	private static final String APIFIELD_RESPONSE_STATUS = "responseStatus";

	private JsonService jsonService = ServiceLocator.locate(JsonService.class);
	private JsonObject rootJson = null;
	private String rawJson = null;

	public VpsAPIResponse(JsonObject jsonResponse) {
		super();

		rootJson = jsonResponse;
		if (rootJson != null) {
			rawJson = rootJson.asString();
		}
	}

	public VpsAPIResponse(String jsonResponse) {
		super();

		rawJson = jsonResponse;
		if (rawJson != null) {
			rootJson = jsonService.readJson(jsonResponse).getJsonObject();
		}
	}

	public JsonArray getErrors() {
		return getArray(APIFIELD_ERRORS);
	}

	public JsonObject getResponseDetails() {
		return getObject(APIFIELD_RESPONSE_DETAILS);
	}

	public String getResponseMessage() {
		return getString(APIFIELD_RESPONSE_MESSAGE);
	}

	public String getResponseStatus() {
		return getString(APIFIELD_RESPONSE_STATUS);
	}

	public JsonArray getArray(String key) {
		JsonArray result = null;
		if (rootJson != null) {
			if (rootJson.contains(key)) {
				result = rootJson.getValue(key, JsonValueType.ARRAY);
			}
		}
		return result;
	}

	public Boolean getBoolean(String key) {
		Boolean result = null;
		if (rootJson != null) {
			if (rootJson.contains(key)) {
				result = rootJson.getValue(key, JsonValueType.BOOLEAN);
			}
		}
		return result;
	}

	public JsonObject getJson() {
		return rootJson;
	}

	public BigDecimal getNumber(String key) {
		BigDecimal result = null;
		if (rootJson != null) {
			if (rootJson.contains(key)) {
				result = rootJson.getValue(key, JsonValueType.NUMBER);
			}
		}
		return result;
	}

	public JsonObject getObject(String key) {
		JsonObject result = null;
		if (rootJson != null) {
			if (rootJson.contains(key)) {
				result = rootJson.getValue(key, JsonValueType.OBJECT);
			}
		}
		return result;
	}


	public String getString(String key) {
		String result = null;
		if (rootJson != null) {
			if (rootJson.contains(key)) {
				result = rootJson.getValue(key, JsonValueType.STRING);
			}
		}
		return result;
	}

	public String toString() {
		return rootJson.toString();
	}
}