package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.util.Log;
import com.veeva.vault.custom.util.TriggerUtil;
import com.veeva.vault.sdk.api.core.BatchOperationError;
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
import java.util.Map;
import java.util.Set;

/**
 * Object: submission_country__rim
 * Trigger API Name: submission_country_sub_fields__c
 * Author: Todd Taylor
 * Date: 11 July 2017 (SDK version 2.2)
 *
 * 1. Update the submission object country reporting field
 * 2. Set submission object country reprocessing status
 *
 */
@RecordTriggerInfo(object = "submission_country__rim", events = {RecordEvent.AFTER_INSERT,RecordEvent.AFTER_DELETE}, name="submission_country_sub_fields__c")
public class SubmissionCountrySubFields implements RecordTrigger {
	
	private static final String COUNTRY_CODE = "country_code__c";
	private static final String REPORTING_COUNTRIES = "reporting_countries__c";
	private static final String COUNTRY_PROCESSING ="country_security_processing_status__c";
	private static final String REPROCESSING_REQUIRED = "reprocessing_required__c";
	private static final String DISPATCH_COUNTRIES = "dispatch_countries__c";
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	
    	// Keep track of nested trigger
    	TriggerUtil.addNestedTriggerContext(SubmissionCountrySubFields.class.toString());
    	
    	Map<String,Set<String>> mapSubmission = VaultCollections.newMap();
    	
    	// Map for handling deletes of countries in trigger context versus queried data
    	Map<String,Set<String>> mapSubmissionDelete = VaultCollections.newMap();
    	
    	try {
    		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
    			// Loop through the trigger context based on the current batch size threshold, grab the countries being added and what already exist
    			
            	String submissionId = "";
            	
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_INSERT)             		
            		submissionId = inputRecord.getNew().getValue("submission__rim", ValueType.STRING);
            	
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE)             		
            		submissionId = inputRecord.getOld().getValue("submission__rim", ValueType.STRING);             		
            	
            	Set<String> setCountry = VaultCollections.newSet();    	
            	if (mapSubmission.containsKey(submissionId)) setCountry = mapSubmission.get(submissionId);           	
            		
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_INSERT)
            		setCountry.add(formatCountryCode(inputRecord.getNew().getValue(COUNTRY_CODE, ValueType.STRING)));
            	
            	mapSubmission.put(submissionId, setCountry);
            	
            	// Deletes need to hold the countries to be deleted. This ensures proper removal of queried data (no enhancement for passing context yet)            	
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) {
            		Set<String> setCountryDelete = VaultCollections.newSet();
            		if (mapSubmissionDelete.containsKey(submissionId)) setCountryDelete = mapSubmissionDelete.get(submissionId);
            		
            		setCountryDelete.add(formatCountryCode(inputRecord.getOld().getValue(COUNTRY_CODE, ValueType.STRING)));           		
            		            		
            		mapSubmissionDelete.put(submissionId, setCountryDelete);
            	}
            	
            	// Perform updates when batch size of submission is encountered
            	if (mapSubmission.size() == BATCH_SIZE) {
            		
	            	// Get all the submission countries to form the reporting picklist
            		mapSubmission = getSubmissionCountries(mapSubmission);
            		            		
	            	// Remove deleted countries from each submission
	            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) 
	            		mapSubmission = processDeletes(mapSubmission,mapSubmissionDelete);
	            	
	            	updateSubmission(mapSubmission);
	            	
            		mapSubmission = VaultCollections.newMap();
            		mapSubmissionDelete = VaultCollections.newMap();
            	}
            }
    		// process anything in the last batch of data
    		if (mapSubmission.size() > 0) {
    			mapSubmission = getSubmissionCountries(mapSubmission);
    			
    			// Remove deleted countries from each submission
            	if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) 
            		mapSubmission = processDeletes(mapSubmission,mapSubmissionDelete);
            	
        		updateSubmission(mapSubmission);
    		}
    	}
        catch(VaultRuntimeException e) {
        	 RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED",
     	            e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
     	    throw rollbackException;
        }            	
    }
    /**
   	 * Query all countries for the submissions in the map
   	 * 
   	 * @param mapSubmission
   	 *            - map of the submissions and countries
   	 * @return
   	 *            - map of the submissions and each submissions countries (in a set as map value)
   	 *            
   	 */     	
            	
	private Map<String,Set<String>> getSubmissionCountries(Map<String,Set<String>> mapSubmission) {
				
		String SINGLE_QUOTE = String.valueOf((char) 39);
		String query = "select submission__rim,country_code__c from submission_country__rim where submission__rim contains (";
		
		for (String submissionId : mapSubmission.keySet()) 
			query +=  SINGLE_QUOTE + submissionId + SINGLE_QUOTE + ",";
		
		// remove the last comma and finish the query string		
		query = query.substring(0, query.length() - 1);
		query += ")";
		
		QueryService queryService = ServiceLocator.locate(QueryService.class);
		QueryResponse queryResponse = queryService.query(query);
		queryResponse.streamResults().forEach(queryResult -> {
			String submissionId = queryResult.getValue("submission__rim", ValueType.STRING);
			Set<String> setCountry = mapSubmission.get(submissionId);
			setCountry.add(formatCountryCode(queryResult.getValue(COUNTRY_CODE, ValueType.STRING)));
			mapSubmission.put(submissionId,setCountry);			
        });
						
		return mapSubmission;
    }
	/**
   	 * Remove any countries that are pending delete
   	 * 
   	 * @param mapSubmission
   	 *            - map of the submissions and countries
   	 * @param mapSubmissionDelete
   	 *            - map of the submissions and countries that are to be deleted
   	 * @return
   	 *            - map of the submissions and each submissions countries (in a set as map value)
   	 *            
   	 */     	
            	
	private Map<String,Set<String>> processDeletes(Map<String,Set<String>> mapSubmission, Map<String,Set<String>> mapSubmissionDelete) {
				
		for (String submissionId : mapSubmissionDelete.keySet()) {
			if (mapSubmission.containsKey(submissionId)) {
				Set<String> setCountry = mapSubmission.get(submissionId);
				Set<String> setCountryDelete = mapSubmissionDelete.get(submissionId);
				
				for (String country : setCountryDelete) {
					if (setCountry.contains(country)) setCountry.remove(country);
				}
				mapSubmission.put(submissionId, setCountry);
			}
			
		}
		return mapSubmission;
    }
	/**
   	 * Update submission record with new submission country info
   	 * 
   	 * @param mapSubmission
   	 *             map of the submissions and countries
   	 *            
   	 */     	
            	
	private void updateSubmission(Map<String,Set<String>> mapSubmission) {
		// Check if this came from Submission before updating the submission
		List<String> nestedTriggerlist = TriggerUtil.getNestedTriggerContext().getTriggerList();
		
		if (!nestedTriggerlist.contains(SubmissionDispatchHandler.class.toString())) {		
			String sErrorMessage = "";
			RecordService recordService = ServiceLocator.locate(RecordService.class);
			List<Record> listRecord = VaultCollections.newList();
					
			try {
				for (String submissionId : mapSubmission.keySet()) {
					List<String> listCountry = VaultCollections.newList();	
				
					for (String s : mapSubmission.get(submissionId)) listCountry.add(s);				
					java.util.Collections.sort(listCountry);
					
					List<String> listReprocess = VaultCollections.newList();
			        listReprocess.add(REPROCESSING_REQUIRED);
			        
					Record r = recordService.newRecord("submission__v");
					
					r.setValue("id", submissionId);
					r.setValue(REPORTING_COUNTRIES, listCountry);
			        r.setValue(COUNTRY_PROCESSING, listReprocess);
			        r.setValue(DISPATCH_COUNTRIES, null); // needed to avoid recursive trigger
			        
			        listRecord.add(r);		        
				}

				saveRecords(listRecord);
			}
			catch(VaultRuntimeException e) {
	    		RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", "Error creating submission countries " + sErrorMessage);
		             
	    	    throw rollbackException;
			}
		} else {
			Log.debug("Nested Trigger detected.  Skipping update to Submission");
		}
    }
	/**
	 * Format the country code to match picklist api name. For example, "USA" becomes "usa__c".
	 * 
	 * @param sCountry
	 *            - the 3 char iso country code
	 * @return
	 *            - formatted api name of the country code
	 */
    private String formatCountryCode(String sCountry) {
    	return sCountry.toLowerCase() + "__c";
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
	        .onSuccesses(batchOperationSuccess -> {
				batchOperationSuccess.stream().forEach(success -> {
					Log.debug("Successfully created/updated record with id: " + success.getRecordId() + " for object");
						
					});
			})	
        	.onErrors(batchOperationErrors -> {
        			batchOperationErrors.stream().findFirst().ifPresent(error -> {
        				String errMsg = error.getError().getMessage();
        				throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
        			});
        	})
        	.execute();
        Log.debug("COmpleted");
    }
   
}