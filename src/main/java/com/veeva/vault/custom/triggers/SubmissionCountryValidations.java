package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;

/**
 * Object: submission_country__rim
 * Trigger API Name: submission_country_validations__c
 * Author: Todd Taylor
 * Date: 11 July 2017
 *
 * Trigger for any validation rules or save edits on Submission Country.
 * 
 * 1. Prevent delete for imported submissions archive_status__v =  “IMPORT_SUCCEEDED”
 *
 */
@RecordTriggerInfo(object = "submission_country__rim", events = {RecordEvent.BEFORE_DELETE}, name="submission_country_validations__c")
public class SubmissionCountryValidations implements RecordTrigger {
		
	private static final String SUBMISSION_RIM = "submission__rim";	
	private static final String QUERY_SUBMISSION = "select id from submission__v where archive_status__v='IMPORT_SUCCEEDED' and id = {id}";
	private static final String ERROR_NO_DELETE = "Unable to delete Submission Country for imported Submissions";
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	final String SINGLE_QUOTE = String.valueOf((char) 39);
    	
        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            try {
                String submissionId = inputRecord.getOld().getValue(SUBMISSION_RIM, ValueType.STRING);
                
            	String query = QUERY_SUBMISSION.replace("{id}", SINGLE_QUOTE + submissionId + SINGLE_QUOTE);
                
                QueryService queryService = ServiceLocator.locate(QueryService.class);
                QueryResponse queryResponse = queryService.query(query);
                
                if (queryResponse.getResultCount() > 0)                 
                	inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_NO_DELETE);
               
            }            
            catch(VaultRuntimeException e) {
            	inputRecord.setError("OPERATION_NOT_ALLOWED",
                        e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
            }            	
        }
    }
}