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
import java.util.List;
import java.util.Set;

/**
 * Object: application__v
 * Trigger API Name: submission_restriction_application__c
 * Author: Todd Taylor
 * Date: 8 June 2017
 *
 * Set sdk_application_type__c if the application type changes for all related submission records.
 * This will fire submission trigger to set the restricted type.
 *
 */
@RecordTriggerInfo(object = "application__v", events = {RecordEvent.AFTER_UPDATE}, name="submission_restriction_application__c")
public class SubmissionRestrictionApplication implements RecordTrigger {
	
	private static final String SDK_APPLICATION_TYPE = "sdk_application_type__c";	
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {
        final String SINGLE_QUOTE = String.valueOf((char) 39);
        List<Record> listRecord = VaultCollections.newList();
        
        try {
        	for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            
                String applicationType = inputRecord.getNew().getValue("application_type__rim", ValueType.STRING);

                if (!applicationType.equals(inputRecord.getOld().getValue("application_type__rim", ValueType.STRING))) {
                	
                	RecordService recordService = ServiceLocator.locate(RecordService.class);
                    
                    
                	QueryService queryService = ServiceLocator.locate(QueryService.class);
                    QueryResponse queryResponse = null;
                    Iterator<QueryResult> iterator = null;
                    
                    // 2. Query all submission records related to the application
                    String query = "select id from submission__v where application__v = "+ SINGLE_QUOTE + inputRecord.getNew().getValue("id", ValueType.STRING) + SINGLE_QUOTE;
                    queryResponse = queryService.query(query);
                    iterator = queryResponse.streamResults().iterator();

                    while (iterator.hasNext()) {
                        QueryResult queryResult = iterator.next();

                        Record r = recordService.newRecord("submission__v");
                        
                        r.setValue("id", queryResult.getValue("id", ValueType.STRING));
                        r.setValue(SDK_APPLICATION_TYPE, applicationType);

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