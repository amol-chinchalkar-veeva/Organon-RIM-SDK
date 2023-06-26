/*
 * --------------------------------------------------------------------
 * UDC:         VpsVQLResponse
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

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.math.BigDecimal;

@UserDefinedClassInfo()
public class VpsVQLResponse extends VpsAPIResponse {

	private static final String APIFIELD_DATA = "data";
	private static final String APIFIELD_TOTAL = "total";

	public VpsVQLResponse(JsonObject jsonResponse) {
		super(jsonResponse);
	}

	public VpsVQLResponse(String jsonResponse) {
		super(jsonResponse);
	}

	public BigDecimal getTotalRecords() throws Exception {
		BigDecimal result = BigDecimal.valueOf(0);
		JsonObject responseDetails = getResponseDetails();
		if (responseDetails != null) {
			if (responseDetails.contains(APIFIELD_TOTAL)) {
				result = responseDetails.getValue(APIFIELD_TOTAL, JsonValueType.NUMBER);
			}
		}
		return result;
	}

	public JsonArray getData() {
		return getArray(APIFIELD_DATA);
	}
}