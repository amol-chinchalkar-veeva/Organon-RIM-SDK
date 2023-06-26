package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Object: application_country__rim
 * Trigger API Name: application_country_processing_status__c
 * Author: Todd Taylor
 * Date: 11 July 2017
 *
 * 1. Set Application object country reprocessing status
 *
 */
@RecordTriggerInfo(object = "application_country__rim", events = {RecordEvent.AFTER_INSERT,RecordEvent.AFTER_DELETE}, name="application_country_processing_status__c")
public class ApplicationCountryProcessingStatus implements RecordTrigger {
	
	private static final String COUNTRY_PROCESSING ="country_security_processing_status__c";
	private static final String REPROCESSING_REQUIRED = "reprocessing_required__c";
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	
    	Map<String,Record> mapApplication = VaultCollections.newMap();
		RecordService recordService = ServiceLocator.locate(RecordService.class);

    	try {
			 
    		List<String> listReprocess = VaultCollections.newList();
            listReprocess.add(REPROCESSING_REQUIRED);
            
	        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
	           
	        	String applicationId = "";
	        	
	        	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_INSERT)             		
	        		applicationId = inputRecord.getNew().getValue("application__rim", ValueType.STRING);
	        	
	        	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE)             		
	        		applicationId = inputRecord.getOld().getValue("application__rim", ValueType.STRING);
	        	
	            Record r = recordService.newRecord("application__v");
	    		r.setValue("id", applicationId);
	            r.setValue(COUNTRY_PROCESSING, listReprocess);
	            
	            mapApplication.put(applicationId,r);
	            
	        	if (mapApplication.size() == BATCH_SIZE) {	        	
	        		// casting is not supported, so need to create the list manually from the map
	        		List<Record> listRecord = VaultCollections.newList();
	    	    	for (Record r1 : mapApplication.values()) listRecord.add(r1);
	    	        
	    	    	saveRecords(listRecord);
	    	        	        		
	                mapApplication = VaultCollections.newMap();
	        	}
	        }   
	        
	        // Process remaining records outside of the batch
	        if (mapApplication.size() > 0 ) {
	        	List<Record> listRecord = VaultCollections.newList();
	    		for (Record r2 : mapApplication.values()) listRecord.add(r2);
	        
	    		saveRecords(listRecord);
	        }
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