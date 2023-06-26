/*
 * --------------------------------------------------------------------
 * Trigger:     SubmissionDispatchRecipientRole
 * Object:      submission__v
 * Author:      bryanchan @ Veeva
 * Created Date:        2020-05-01
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Creates Dispatch Recipient objects based on the viewer role  
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

import com.veeva.vault.custom.model.DispatchRecordContext;
import com.veeva.vault.custom.util.RecordServiceUtil;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.role.RecordRoleChange;
import com.veeva.vault.sdk.api.role.RecordRoleEvent;
import com.veeva.vault.sdk.api.role.RecordRoleTrigger;
import com.veeva.vault.sdk.api.role.RecordRoleTriggerContext;
import com.veeva.vault.sdk.api.role.RecordRoleTriggerInfo;


@RecordRoleTriggerInfo(object = "submission__v", events = {RecordRoleEvent.BEFORE})
public class SubmissionDispatchRecipientRole implements RecordRoleTrigger {	

	private static final String DISPATCH_CONTEXT ="DISPATCH";
	private static final String DISPATCH_ROLE ="viewer__v";
	private static final String DISPATCH_RECIPIENT_LABEL = "Dispatch Recipients";
	private static final String DISPATCH_RECIPIENT_OBJECT ="dispatch_recipient__c";
	private static final String FIELD_APPLICATION ="application__c";
	private static final String FIELD_SUBMISSION ="submission__c";
	private static final String FIELD_DISPATCH ="dispatch__c";
	private static final String FIELD_USER ="user__c";
	
	/* 
	 * Main Execution for when roles are updated.
	 * (non-Javadoc)
	 * @see com.veeva.vault.sdk.api.role.RecordRoleTrigger#execute(com.veeva.vault.sdk.api.role.RecordRoleTriggerContext)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute(RecordRoleTriggerContext recordRoleTriggerContext) {
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		DispatchRecordContext dispatchRecords = RequestContext.get().getValue(DISPATCH_CONTEXT, DispatchRecordContext.class);
		// Only if there is a dispatch, then we fire this trigger
		if (dispatchRecords != null && dispatchRecords.getDispatchIds().size() > 0) {			
			List<Record> recordList = VaultCollections.newList();
			// Get the roles that got added
			List<RecordRoleChange> rrchanges = recordRoleTriggerContext.getRecordRoleChanges(); 
			for (RecordRoleChange rrchange : rrchanges) {
				// Only get the viewer role
				if (rrchange.getRole().getRoleName().equals(DISPATCH_ROLE)) {
					List<String> usersAdded = rrchange.getUsersAdded();
					// Only if there are new users added
					if (usersAdded.size() > 0) {			
						// Create a record for each dispatch/user combination
						for (String dispatchId : dispatchRecords.getDispatchIds()) {
							for (String user : usersAdded) {
								Record record = recordService.newRecord(DISPATCH_RECIPIENT_OBJECT);
								record.setValue(FIELD_APPLICATION, dispatchRecords.getApplicationId());
								record.setValue(FIELD_SUBMISSION, dispatchRecords.getSubmissionId());
								record.setValue(FIELD_DISPATCH,  dispatchId);
								record.setValue(FIELD_USER, user);
								recordList.add(record);									
							}														
						}											
					}
				}
			}			
			
			// Bulk saves the records
			if (recordList.size() > 0) {
				RecordServiceUtil.updateRecordList(recordList, DISPATCH_RECIPIENT_LABEL);
			}
			
		} 

	}
	
}