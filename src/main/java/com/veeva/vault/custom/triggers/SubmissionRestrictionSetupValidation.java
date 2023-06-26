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
 * Trigger API Name: submission_restriction_setup_validation__c
 * Author: Todd Taylor
 * Date: 12 July 2017
 *
 * 1. Prevent changes to the application type or submission type
 * 2. Set the external id to ensure uniqueness
 *
 */
@RecordTriggerInfo(object = "sa_restricted_submission_type__c", events = {RecordEvent.BEFORE_INSERT,RecordEvent.BEFORE_UPDATE}, name="submission_restriction_setup_validation__c")
public class SubmissionRestrictionSetupValidation implements RecordTrigger {
	
	// Fields on sa_restricted_submission_type__c
	private static final String SUBMISSION_TYPE = "submission_type__c";
	private static final String APPLICATION_TYPE = "application_type__c";
	private static final String RESTRICTION_TYPE = "restriction_type__c";
	private static final String RESTRICTION_TYPE_DRAFT = "restricted_type_draft__c";
	
	private static final String ERROR_NO_CHANGES = "Cannot change submission type, application type, restriction type, or restriction type draft";
	
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {
        final String SINGLE_QUOTE = String.valueOf((char) 39);
        
        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            try {
            	String submissionType = inputRecord.getNew().getValue(SUBMISSION_TYPE, ValueType.STRING);
        		String applicationType = inputRecord.getNew().getValue(APPLICATION_TYPE, ValueType.STRING);
        		List<String> restrictionType = inputRecord.getNew().getValue(RESTRICTION_TYPE, ValueType.PICKLIST_VALUES);
        		List<String> restrictionTypeDraft = inputRecord.getNew().getValue(RESTRICTION_TYPE_DRAFT, ValueType.PICKLIST_VALUES);
        		
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
            		String submissionTypeOld = inputRecord.getOld().getValue(SUBMISSION_TYPE, ValueType.STRING);
            		String applicationTypeOld = inputRecord.getOld().getValue(APPLICATION_TYPE, ValueType.STRING);
            		List<String> restrictionTypeOld = inputRecord.getOld().getValue(RESTRICTION_TYPE, ValueType.PICKLIST_VALUES);
            		List<String> restrictionTypeDraftOld = inputRecord.getOld().getValue(RESTRICTION_TYPE_DRAFT, ValueType.PICKLIST_VALUES);
            		
            		if (!submissionType.equals(submissionTypeOld) || !applicationType.equals(applicationTypeOld) || 
            				!restrictionType.get(0).equals(restrictionTypeOld.get(0)) || !restrictionTypeDraft.get(0).equals(restrictionTypeDraftOld.get(0)))
            			inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_NO_CHANGES);
            	}
            	
            	inputRecord.getNew().setValue("external_id__c", submissionType + "_" + applicationType);
            	            	
            }
            catch(VaultRuntimeException e) {
            	 inputRecord.setError("OPERATION_NOT_ALLOWED",
                         e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
            }
        }
    }    
}