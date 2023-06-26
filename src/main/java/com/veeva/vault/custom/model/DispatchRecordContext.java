/*
 * --------------------------------------------------------------------
 * Model:       DispatchRecordContext
 * Author:      bryanchan @ Veeva
 * Created Date:        2020-05-01
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Description:  Stores a list of dispatch record ids 
 * 				 based on an application and submission
 * 
 * Revision:
 * 2020-05-01: 2.8 Release. 
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.model;

import java.util.List;

import com.veeva.vault.sdk.api.core.RequestContextValue;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;

@UserDefinedClassInfo
public class DispatchRecordContext implements RequestContextValue {
	
	@SuppressWarnings("unchecked")
	private List<String> dispatchIds = VaultCollections.newList();
	private String applicationId;
	private String submissionId;

	public List<String> getDispatchIds() {
		return dispatchIds;
	}

	public void setDispatchIds(List<String> dispatchIds) {
		this.dispatchIds = dispatchIds;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public String getSubmissionId() {
		return submissionId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public void setSubmissionId(String submissionId) {
		this.submissionId = submissionId;
	}
	
}