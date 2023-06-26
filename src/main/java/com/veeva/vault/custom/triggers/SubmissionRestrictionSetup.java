package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * Object: sa_restricted_submission_type__c
 * Trigger API Name: submission_restriction_setup__c
 * Author: Todd Taylor
 * Date: 8 June 2017
 *
 * Set submission sdk_sa_restricted_type__c for new/changed/deleted data in the SA restricted type object
 * This will fire submission trigger to set the restricted type.
 *
 */
@RecordTriggerInfo(object = "sa_restricted_submission_type__c", events = {RecordEvent.AFTER_INSERT,RecordEvent.AFTER_UPDATE,RecordEvent.AFTER_DELETE}, name="submission_restriction_setup__c")
public class SubmissionRestrictionSetup implements RecordTrigger {
	
	// Fields on sa_restricted_submission_type__c
	private static final String SUBMISSION_TYPE = "submission_type__c";
	private static final String APPLICATION_TYPE = "application_type__c";
	private static final String RESTRICTION_TYPE = "restriction_type__c";
	private static final String RESTRICTION_TYPE_DRAFT = "restricted_type_draft__c";
	
	// submission_type_restriction__c picklist values
	private static final String RESTRICTED = "restricted__c";
	private static final String NONRESTRICTED = "nonrestricted__c";
	private static final String RESTRICTED_DRAFT = "restricted_draft__c";
	private static final String NONRESTRICTED_DRAFT = "nonrestricted_draft__c";
	private static final String SAFETY = "safety__c";
	private static final String SAFETY_DRAFT = "safety_draft__c";

	// Submission fields
	private static final String SDK_APPLICATION_TYPE = "sdk_application_type__c";
	private static final String SDK_RESTRICTED_TYPE = "sdk_sa_restricted_type__c";
	private static final String SDK_RESTRICTED_TYPE_YES = "yes"; // use yes/no text values instead of boolean 
	private static final String SDK_RESTRICTED_TYPE_NO = "no";
	
	private static final String LIFECYCLE_STATE_FINAL = "final_state__c"; 
	private static final String LIFECYCLE_STATE_ASSEMBLY = "in_assembly_review_state__c";
	
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {
        final String SINGLE_QUOTE = String.valueOf((char) 39);
        
        List<Record> listRecord = VaultCollections.newList();
        
        try {
        	for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {            
            	/* 
                 * Criteria for running trigger logic:
                 * 1. After insert - populate SDK_APPLICATION_TYPE, SDK_RESTRICTED_TYPE as yes
                 * 2. After delete - populate SDK_APPLICATION_TYPE, SDK_RESTRICTED_TYPE as no 
                 * 3. After update with status__v changed to active - same #1
                 * 4. After update with status__v changed to inactive - same #2
                 *  
                 * Note that a before trigger prevents updates to the application and submission type fields
                 */
            	String submissionType = "";
            	String applicationType = "";
            	String restrictedTypeYesNo = "";
            	String restrictionType = "";
            	String restrictionTypeDraft = "";
            	
            	// scenario 2
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) {
            		submissionType = inputRecord.getOld().getValue(SUBMISSION_TYPE, ValueType.STRING);
                	applicationType = inputRecord.getOld().getValue(APPLICATION_TYPE, ValueType.STRING);
                	restrictedTypeYesNo = SDK_RESTRICTED_TYPE_NO;
            	}
            	else {
            		List<String> listStatusNew = inputRecord.getNew().getValue("status__v", ValueType.PICKLIST_VALUES);
            		String statusNew = listStatusNew.get(0);
            		
            		if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_INSERT) {
            			// scenario 1
	            		submissionType = inputRecord.getNew().getValue(SUBMISSION_TYPE, ValueType.STRING);
	                	applicationType = inputRecord.getNew().getValue(APPLICATION_TYPE, ValueType.STRING);
	                	restrictionType = (String) inputRecord.getNew().getValue(RESTRICTION_TYPE, ValueType.PICKLIST_VALUES).get(0);
	                	restrictionTypeDraft = (String) inputRecord.getNew().getValue(RESTRICTION_TYPE_DRAFT, ValueType.PICKLIST_VALUES).get(0);
	                	
	                	if (statusNew.equalsIgnoreCase("active__v")) restrictedTypeYesNo = SDK_RESTRICTED_TYPE_YES;
	                	else restrictedTypeYesNo = SDK_RESTRICTED_TYPE_NO;
            		}            		
            		else {            		
            			List<String> listStatusOld = inputRecord.getOld().getValue("status__v", ValueType.PICKLIST_VALUES);
                		String statusOld = listStatusOld.get(0);
                		
                		if (!statusNew.equals(statusOld)) {
                			// scenarios 3 and 4
                			submissionType = inputRecord.getNew().getValue(SUBMISSION_TYPE, ValueType.STRING);
    	                	applicationType = inputRecord.getNew().getValue(APPLICATION_TYPE, ValueType.STRING);
    	                	restrictionType = (String) inputRecord.getNew().getValue(RESTRICTION_TYPE, ValueType.PICKLIST_VALUES).get(0);
    	                	restrictionTypeDraft = (String) inputRecord.getNew().getValue(RESTRICTION_TYPE_DRAFT, ValueType.PICKLIST_VALUES).get(0);
    	                	
    	                	if (statusNew.equalsIgnoreCase("active__v")) restrictedTypeYesNo = SDK_RESTRICTED_TYPE_YES;
    	                	else restrictedTypeYesNo = SDK_RESTRICTED_TYPE_NO;
                		}
            		}
            	}
            	            	
            	// Continue if submissionType was set - Retrieve and update all submission records
            	if (!submissionType.equals("")) {
            		RecordService recordService = ServiceLocator.locate(RecordService.class);
                    
                    
                	QueryService queryService = ServiceLocator.locate(QueryService.class);
                    QueryResponse queryResponse = null;
                    Iterator<QueryResult> iterator = null;
                    
                    // 2. Query all submission records related to the application
                    String query = "select id,state__v from submission__v where submission_type__rim = "+ SINGLE_QUOTE + submissionType + SINGLE_QUOTE;
                    queryResponse = queryService.query(query);
                    iterator = queryResponse.streamResults().iterator();

                    while (iterator.hasNext()) {
                        QueryResult queryResult = iterator.next();

                        Record r = recordService.newRecord("submission__v");
                        
                        r.setValue("id", queryResult.getValue("id", ValueType.STRING));
                        r.setValue(SDK_APPLICATION_TYPE, applicationType);
                        String submissionState = queryResult.getValue("state__v", ValueType.STRING);
                        boolean stateFinal = false;
                        if (submissionState != null && (submissionState.equalsIgnoreCase(LIFECYCLE_STATE_FINAL) || submissionState.equalsIgnoreCase(LIFECYCLE_STATE_ASSEMBLY))) {
                        	stateFinal = true;
                        }
                        
                        if (restrictedTypeYesNo.equals(this.SDK_RESTRICTED_TYPE_YES)) {
                        	if (stateFinal) {
                            	r.setValue(SDK_RESTRICTED_TYPE, restrictionType);
                            } else {
                            	r.setValue(SDK_RESTRICTED_TYPE, restrictionTypeDraft);
                            }
                        } else {
                        	r.setValue(SDK_RESTRICTED_TYPE, restrictedTypeYesNo);
                        }

                        listRecord.add(r);
                        
                        if (listRecord.size() == BATCH_SIZE) {
        	    	    	saveRecords(listRecord);
        	    	        	        		
        	    	    	listRecord = VaultCollections.newList();
        	        	}
                    }
            	}
            }  
        	
        	// Process remaining records outside of the batch
 	        if (listRecord.size() > 0) saveRecords(listRecord);
        }
        catch(VaultRuntimeException e) {
        	RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED",
     	            e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
     	    throw rollbackException;

        }
    } 
    /**
   	 * Perform a DML save operation on a list of records.
   	 * Rollback the entire transaction when encountering errors.
   	 * 
   	 * @param listRecord
   	 *            - list of records to perform the save upon
   	 */
    private void saveRecords(List<Record> listRecord) {
		RecordService recordService = ServiceLocator.locate(RecordService.class);

        recordService.batchSaveRecords(listRecord)
        	.onErrors(batchOperationErrors -> {
        			batchOperationErrors.stream().findFirst().ifPresent(error -> {
        				String errMsg = error.getError().getMessage();
        				throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
        			});
        	})
        	.execute();
    }
}