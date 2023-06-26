/*
 * --------------------------------------------------------------------
 * Trigger:     SubmissionDispatchRecipient
 * Object:      submission__v
 * Author:      bryanchan @ Veeva
 * Created Date:        2020-05-01
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Description: 
 * 
 * If a submission goes to the final state:
 *   - Find all related dispatch recipients and set pending to false.
 * If a submission goes back to draft:
 *   - Find all related dispatch recipients and delete them    
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
package com.veeva.vault.custom.triggers;

import java.util.List;

import com.veeva.vault.custom.util.QueryServiceUtil;
import com.veeva.vault.custom.util.RecordServiceUtil;
import com.veeva.vault.custom.util.TriggerUtil;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.query.QueryResponse;

@RecordTriggerInfo(object = "submission__v", events = {RecordEvent.BEFORE_UPDATE})
public class SubmissionDispatchRecipient implements RecordTrigger {
	
	private static final String FIELD_LIFECYCLE_STATE = "state__v";
	private static final String DISPATCH_RECIPIENT_OBJECT ="dispatch_recipient__c";
	private static final String DISPATCH_RECIPIENT_LABEL = "Dispatch Recipients";
	private static final String STATE_FINAL = "final_state__c";
	private static final String STATE_ACTIVE = "active_state__c";
	private static final String FIELD_PENDING = "pending__c";
	private static final String VQL_PENDING_DISPATCH_RECIPIENT = "SELECT id FROM dispatch_recipient__c "
			+ "WHERE pending__c=true AND submission__c={submission_id}";
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	RecordService recordService = ServiceLocator.locate(RecordService.class);
    	List<Record> recordList = VaultCollections.newList();
    	List<Record> recordDeleteList = VaultCollections.newList();
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			// Detect if a state has changed on the submission 
			if (TriggerUtil.fieldChanged(inputRecord, FIELD_LIFECYCLE_STATE, ValueType.STRING, recordTriggerContext.getRecordEvent())) {
				String submissionId = inputRecord.getNew().getValue("id", ValueType.STRING);
				String state = inputRecord.getNew().getValue(FIELD_LIFECYCLE_STATE, ValueType.STRING);
				
				// Find all the existing dispatch recipient where pending is true for this submission.
				QueryResponse queryResponse = QueryServiceUtil.query(VQL_PENDING_DISPATCH_RECIPIENT
						.replace("{submission_id}", "'" + submissionId + "'"));
				queryResponse.streamResults().forEach(queryResult -> {
					Record record = recordService.newRecordWithId(DISPATCH_RECIPIENT_OBJECT, queryResult.getValue("id", ValueType.STRING));
					// if the new state is final, set pending to true
					if (state.equals(STATE_FINAL)) {
						boolean pending = false;
						record.setValue(FIELD_PENDING, pending);
						recordList.add(record);
					// if the new state is active, add this record to be deleted.	
					} else if (state.equals(STATE_ACTIVE)) {
						recordDeleteList.add(record);
					}
				});
			}
		}
		
		if (recordList.size() > 0) {
			RecordServiceUtil.updateRecordList(recordList, DISPATCH_RECIPIENT_LABEL);
		}
		
		if (recordDeleteList.size() > 0) {
			RecordServiceUtil.deleteRecordList(recordDeleteList, DISPATCH_RECIPIENT_LABEL);
		}
    }
}