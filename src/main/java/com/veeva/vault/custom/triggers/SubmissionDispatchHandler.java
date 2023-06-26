/*
 * Object: submission__v
 * Trigger API Name: submission_dispatch_handler__c
 * Author: Todd Taylor
 * Created Date:        2017-06-01
 * Last Modifed Date:   2020-05-01
 *
 * Initiate dispatch workflow for planned activities from the event.
 * 1. User starts dispatch create request and adds the countries (field: dispatch_countries__c). Check for existing dispatch records.
 * 2. Trigger creates submission country (submission_country__rim) records if new country is introduced (query before insert)
 * 3. Trigger creates submission dispatch records (if it does not already exist)
 *    - System fires DAC to populate default users/roles on sharing settings
 * 4. Trigger initiates workflow on the dispatch records
 * 5. Trigger nulls dispatch_countries__c
 *
 * Additional Rules
 * A1. Set country_security_processing_status__c to reprocessing required (reprocessing_required__c) when adding dispatch countries
 * A2. Set the country reporting field
 * A3. When Submissions Archive Status is changed to IMPORT_SUCCEEDED country_security_processing_status__c to reprocessing required (reprocessing_required__c)
 *
 * INACTIVE A4. Stamp the person who finalizes the submission
 *
 * Revision:
 * 2020-05-01: 2.8 Release. Added related object support
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.model.DispatchRecordContext;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.custom.util.TriggerUtil;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RecordTriggerInfo(object = "submission__v", events = {RecordEvent.BEFORE_UPDATE})
public class SubmissionDispatchHandler implements RecordTrigger {

	private static final String DISPATCH_COUNTRIES = "dispatch_countries__c";
	private static final String PRODUCT_RESTRICTION = "product_restriction__c";
	private static final String COUNTRY_PROCESSING ="country_security_processing_status__c";
	private static final String REPROCESSING_REQUIRED = "reprocessing_required__c";
	private static final String REPORTING_COUNTRIES = "reporting_countries__c";
	private static final String ARCHIVE_STATUS = "archive_status__v";
	private static final String IMPORT_SUCCEEDED = "IMPORT_SUCCEEDED";
	private static final String LIFECYCLE_STATE_FINAL = "final_state__c";

	private static final String OBJECT_DISPATCH = "dispatch__c";

	private static final String USER_ACTION_DISPATCH = "send_corporate_package_useraction1__c";

	private static final String ERROR_EXISTING_DISPATCHES = "Unable to send dispatches. Dispatches exist for the following countries: ";
	private static final String ERROR_SUB_COUNTRIES = "Unable to create submission countries";

	private static final int BATCH_SIZE = 500;

    public void execute(RecordTriggerContext recordTriggerContext) {
    	final String methodName = SubmissionDispatchHandler.class.toString() + ".execute()";
    	Log.entry(methodName);

    	// 19R1 Changes:  Keep track of nested trigger to prevent optistimic locking
        TriggerUtil.addNestedTriggerContext(SubmissionDispatchHandler.class.toString());


        boolean bCountryReprocess = false;

        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            try {
            	// A3. Look for change in archive status
            	String archiveStatusNew = inputRecord.getNew().getValue(ARCHIVE_STATUS, ValueType.STRING);
            	String archiveStatusOld = inputRecord.getOld().getValue(ARCHIVE_STATUS, ValueType.STRING);
            	if (!isEqual(archiveStatusNew,archiveStatusOld) && archiveStatusNew != null && archiveStatusNew.equals(IMPORT_SUCCEEDED)) {
            		List<String> listTemp = VaultCollections.newList();
        			listTemp.add(REPROCESSING_REQUIRED);
        			inputRecord.getNew().setValue(COUNTRY_PROCESSING, listTemp);
            	}

            	// A4. Finalizer
            	/*
            	String stateNew = inputRecord.getNew().getValue("state__v", ValueType.STRING);
            	String stateOld = inputRecord.getOld().getValue("state__v", ValueType.STRING);
            	if (!isEqual(stateNew,stateOld) && stateNew != null && stateNew.equals(LIFECYCLE_STATE_FINAL)) 
            		inputRecord.getNew().setValue("finalizer__c", RequestContext.get().getCurrentUserId());            		
            	*/


            	// Run dispatch trigger logic if dispatch countries field is populated
            	List<String> listNewCountries = inputRecord.getNew().getValue(DISPATCH_COUNTRIES, ValueType.PICKLIST_VALUES);
            	if (listNewCountries != null && !listNewCountries.isEmpty()) {

            		String applicationId = inputRecord.getNew().getValue("application__v", ValueType.STRING);
            		String submissionId = inputRecord.getNew().getValue("id", ValueType.STRING);

            		Set<String> setNewCountries = VaultCollections.newSet();
            		for (String s : listNewCountries)
            			setNewCountries.add(s);

            		// 1 Check for existing dispatches
            		String sResults = checkForExistingDispatches(submissionId, setNewCountries);

            		if (sResults.equals("")) {

            			// 2 Create submission country records
                		if(createSubmissionCountries(submissionId, setNewCountries)) {

                			// 3 Create dispatch records
                			Map<String,List<String>> mapCountries = getSubmissionCountries(submissionId);

                			List<String> srr = createDispatches(submissionId,mapCountries,setNewCountries);

                			// Release 2.8 Add the dispatch records to record context
                			addDispatchRecordContext(applicationId, submissionId, srr);

                			// 4 Initiate the workflow
                			if (srr != null) startDispatchWorkflow(srr);

                			// 5 All done, null dispatch countries for next dispatch
                			//AVC//inputRecord.getNew().setValue(DISPATCH_COUNTRIES, null);

                			// A1 Set country_security_processing_status__c (new countries have been added)
                			List<String> listTemp = VaultCollections.newList();
                			listTemp.add(REPROCESSING_REQUIRED);
                			inputRecord.getNew().setValue(COUNTRY_PROCESSING, listTemp);

                			// A2 Set the reporting countries field

                			List<String> listCountry = VaultCollections.newList();
                			for (String s : mapCountries.keySet()) listCountry.add(s);
                			java.util.Collections.sort(listCountry);
                			inputRecord.getNew().setValue(REPORTING_COUNTRIES, listCountry);
                		}
                		else {
                			inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_SUB_COUNTRIES);
                			Log.error(ERROR_SUB_COUNTRIES);
                		}

            		}
            		else {
            			// 1 Dispatches already exist
            			inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_EXISTING_DISPATCHES + sResults);
            			Log.error(ERROR_SUB_COUNTRIES);
            		}
            	}
            	//AVC//	 inputRecord.getNew().setValue(DISPATCH_COUNTRIES, null);
            }
            catch(VaultRuntimeException e) {
            	 inputRecord.setError("OPERATION_NOT_ALLOWED",
                         e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
            }
        }

        Log.exit(methodName);
    }
    /**
   	 * Format the country code to match picklist api name. For example, "USA" becomes "usa__c".
   	 *
   	 * @param submissionId
   	 *            - the current submission id
   	 * @param setNewCountries
   	 *            - set of new countries requesting a dispatch
   	 * @return
   	 *            - error string of the countries that already have dispatches
   	 */
    public String checkForExistingDispatches(String submissionId, Set<String> setNewCountries) {
    	final String methodName = SubmissionDispatchHandler.class.toString() + ".checkForExistingDispatches()";
    	Log.entry(methodName);
    	String sErrorMessage = "";

    	//String SINGLE_QUOTE = String.valueOf((char) 39);
    	String query = "select id,market1__cr.country_code__rim,market1__cr.name__v from dispatch__c where submission__c='" + submissionId + "'";

		QueryService queryService = ServiceLocator.locate(QueryService.class);
		QueryResponse queryResponse = queryService.query(query);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

		while (iterator.hasNext()) {
            QueryResult queryResult = iterator.next();
            String country = formatCountryCode(queryResult.getValue("market1__cr.country_code__rim", ValueType.STRING));

            if (country.equals("")) {
            	sErrorMessage += sErrorMessage + "Invalid country data for " + country;
            }

            if (setNewCountries.contains(country))
            	sErrorMessage += queryResult.getValue("market1__cr.name__v", ValueType.STRING)+ ",";
        }
		if (!sErrorMessage.equals(""))
			sErrorMessage = sErrorMessage.substring(0, sErrorMessage.length() - 1);

		Log.exit(methodName);
		return sErrorMessage;
    }
    /**
   	 * Create the Submission Country records if they do not exist
   	 *
   	 * @param submissionId
   	 *            - the current submission id
   	 * @param setNewCountries
   	 *            - set of new countries requesting a dispatch
   	 * @return
   	 *            - true for new records, false for no records/failure
   	 */
    private boolean createSubmissionCountries(String submissionId, Set<String> setNewCountries) {
    	final String methodName = SubmissionDispatchHandler.class.toString() + ".createSubmissionCountries()";
    	Log.entry(methodName);

    	String sErrorMessage = "";
    	boolean ret = false;
    	//String SINGLE_QUOTE = String.valueOf((char) 39);

    	Map<String,List<String>> mapCountries = getSubmissionCountries(submissionId);

    	Set<String> setCountriesToAdd = VaultCollections.newSet();
    	for (String s : setNewCountries) setCountriesToAdd.add(s);

    	try {
	    	if (mapCountries != null) {
	    		// Determine which countries need records
	    		for (String s : mapCountries.keySet()) {
	    			if (setNewCountries.contains(s))
	    				setCountriesToAdd.remove(s);
	    		}
	    	}

			if (setCountriesToAdd.size() > 0) {
				String queryContains = "";

	            for (String s : setCountriesToAdd) {
	                if (!queryContains.equals(""))
	                    queryContains += ",";

	                queryContains += "'" + (s.substring(0, s.length() - 3)).toUpperCase() + "'";
	            }

	            RecordService recordService = ServiceLocator.locate(RecordService.class);
				QueryService queryService = ServiceLocator.locate(QueryService.class);

				QueryResponse queryResponse = queryService.query("select id, country_code__rim, name__v from country__v where country_code__rim contains (" + queryContains + ")");
				Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

				List<Record> listRecord = VaultCollections.newList();

	            while (iterator.hasNext()) {
	                QueryResult queryResult = iterator.next();

	                Record r = recordService.newRecord("submission_country__rim");

	                r.setValue("submission__rim", submissionId);
	                //r.setValue("name__v", queryResult.getValue("name__v", ValueType.STRING));
	                r.setValue("country__rim", queryResult.getValue("id", ValueType.STRING));

	                List<String> orion = VaultCollections.newList();
	                orion.add("no__c");
	                r.setValue("orion_record__c", orion);

	                listRecord.add(r);
	            }



	            if (listRecord.size() > 0) {
	    			List<List<Record>> batchRecords = partition(listRecord);

	    			for (List<Record> batchRecord : batchRecords) {
	    				Log.debug("Saving batch START");
	    				recordService.batchSaveRecords(batchRecord)
	    					.onSuccesses(batchOperationSuccess -> {
	    						batchOperationSuccess.stream().forEach(success -> {
	    							Log.debug("Successfully created/updated record with id: " + success.getRecordId() + " for object [submission_country__rim]");
	    							});
	    					})
	    					.onErrors(batchOperationErrors -> {
	    							batchOperationErrors.stream().forEach(error -> {
	    								String errMsg = error.getError().getMessage();
	    								int errPosition = error.getInputPosition();
	    								String id = listRecord.get(errPosition).getValue("id", ValueType.STRING);
	    								final String message = "Error creating submission countries " + errMsg;
    									Log.debug(message + ", record id:" + id);
    									throw new RollbackException("OPERATION_NOT_ALLOWED", message);
	    							});
	    					}).execute();
	    				Log.debug("Saving batch END");
	    			}
	    			ret = true;
	    		}
			}
			else {
				ret = true;
			}
		}
    	catch(VaultRuntimeException e) {
    		RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", "Error creating submission countries " + sErrorMessage);

    	    throw rollbackException;
       }

    	Log.exit(methodName);
		return ret;
    }
    /**
   	 * Return all submission countries for the current submission
   	 *
   	 * @param submissionId
   	 *            - the current submission id
   	 * @return
   	 *            - map of the returned countries, keyed by country code in xxx__c format and a list of strings containing<br>
   	 *            0 - id of the record<br>
   	 *            1 - country code<br>
   	 *            2 - name of the country<br><br>
   	 *
   	 *            Array is used as helper class is not supported, so access is via known index in to the array
   	 *
   	 */

	private Map<String,List<String>> getSubmissionCountries(String submissionId) {
		final String methodName = SubmissionDispatchHandler.class.toString() + ".getSubmissionCountries()";
    	Log.entry(methodName);
		Map<String,List<String>> mapSubmissionCountries = VaultCollections.newMap();

		//String SINGLE_QUOTE = String.valueOf((char) 39);
		String query = "select id,country__rimr.country_code__rim,country__rimr.name__v from submission_country__rim where submission__rim='" + submissionId + "'";

		QueryService queryService = ServiceLocator.locate(QueryService.class);
		QueryResponse queryResponse = queryService.query(query);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

		while (iterator.hasNext()) {
		    QueryResult queryResult = iterator.next();
		    String country = formatCountryCode(queryResult.getValue("country__rimr.country_code__rim", ValueType.STRING));

		    List<String> l = VaultCollections.newList();
		    l.add(queryResult.getValue("id", ValueType.STRING));  // 0
		    l.add(queryResult.getValue("country__rimr.country_code__rim", ValueType.STRING));  // 1
		    l.add(queryResult.getValue("country__rimr.name__v", ValueType.STRING));  // 2

		    mapSubmissionCountries.put(country, l);
		}

		Log.exit(methodName);
		return mapSubmissionCountries;
    }
    /**
   	 * Create the Dispatch records for the new countries
   	 *
   	 * @param submissionId
   	 *            - the current submission id
   	 * @param mapCountries
   	 * 			  - map of the submission countries to grab that id
   	 * @param setNewCountries
   	 *            - set of new countries requesting a dispatch
   	 * @return
   	 *            - ids of the the saved records
   	 */
    public List<String> createDispatches(String submissionId, Map<String,List<String>> mapCountries,Set<String> setNewCountries) {
    	final String methodName = SubmissionDispatchHandler.class.toString() + ".createDispatches()";
    	Log.entry(methodName);
    	List<Record> listRecord = VaultCollections.newList();
    	List<String> dispatchedIds = VaultCollections.newList();
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		for (String s : setNewCountries) {
			if (mapCountries.containsKey(s)) {
    			Record r = recordService.newRecord(OBJECT_DISPATCH);

                r.setValue("name__v", "Dispatch to " + mapCountries.get(s).get(2));
                r.setValue("submission_country__c", mapCountries.get(s).get(0));
                r.setValue("submission__c", submissionId);

                listRecord.add(r);
			}
		}

		if (listRecord.size() > 0) {
			List<List<Record>> batchRecords = partition(listRecord);

			for (List<Record> batchRecord : batchRecords) {
				Log.debug("Saving batch START");
				recordService.batchSaveRecords(batchRecord)
					.onSuccesses(batchOperationSuccess -> {
						batchOperationSuccess.stream().forEach(success -> {
							Log.debug("Successfully created/updated record with id: " + success.getRecordId() + " for object [" + OBJECT_DISPATCH + "]");
							dispatchedIds.add(success.getRecordId());
							});
					})
					.onErrors(batchOperationErrors -> {
							batchOperationErrors.stream().forEach(error -> {
								String errMsg = error.getError().getMessage();
								int errPosition = error.getInputPosition();
								String id = listRecord.get(errPosition).getValue("id", ValueType.STRING);
								final String message = "Error creating submission dispatches: " + errMsg;
								Log.debug(message + ", record id:" + id);
								throw new RollbackException("OPERATION_NOT_ALLOWED", message);
							});
					}).execute();
				Log.debug("Saving batch END");
			}
		}

		Log.exit(methodName);
		return dispatchedIds;
    }

    /**
     * Starts a dispatch workflow
     *
     * @param dispatchIds
     * 			- the list of dispatch ids to send for the dispatch workflow
     */
    private void startDispatchWorkflow(List<String> dispatchIds) {
    	final String methodName = SubmissionDispatchHandler.class.toString() + ".startDispatchWorkflow()";
    	Log.entry(methodName);
    	RecordService recordService = ServiceLocator.locate(RecordService.class);
    	List<Record> listRecord = VaultCollections.newList();
    	dispatchIds.forEach(recordsId -> {
    		listRecord.add(recordService.newRecordWithId(OBJECT_DISPATCH, recordsId));
    	});

    	if (listRecord.size() > 0) {
	        JobService jobService = ServiceLocator.locate(JobService.class);
	        JobParameters jobParameters = jobService.newJobParameters("record_user_action__v");

	        jobParameters.setValue("user_action_name", USER_ACTION_DISPATCH);
	        jobParameters.setValue("records", listRecord);

	        jobService.run(jobParameters);
    	}
    	Log.exit(methodName);
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
    	if (sCountry == null || sCountry.equals("")) return "";
    	else return sCountry.toLowerCase() + "__c";
    }
    /**
  	 * Determine if the two objects are equal
  	 *
  	 * @param o1
  	 *            - first object to compare
  	 * @param o2
  	 *            - second object to compare
  	 * @return
  	 *            - true if equal, false if not
  	 */
    public static boolean isEqual(Object o1, Object o2) {
    	return o1 == null ? o2 == null :o1.equals(o2);
    }

	/**
	 * partitions a list into a multiple list 
	 * based on the batch size
	 * @param recordList the list of records to be partitioned
	 * @return multiple list of records based on the batch
	 */
	@SuppressWarnings("unchecked")
	private List<List<Record>> partition(List<Record> recordList) {
		List<List<Record>> partitions = VaultCollections.newList();

		for (int i=0; i<recordList.size(); i += BATCH_SIZE) {
	        partitions.add(recordList.subList(i, min(i + BATCH_SIZE, recordList.size())));
	    }

		return partitions;
	}

	/**
	 * A copy of the Math.min library. 
	 * Return the minimum value between a and b
	 * @param a integer 1
	 * @param b integer 2
	 * @return the integer that has the lower value
	 */
	private static int min(int a, int b) {
		return (a > b) ?  b : a;
	}


	/**
	 * 2.8 Release
	 * Adds a list of dispatch ids, the application id, and submission id
	 * to the dispatch record context.
	 *
	 * @param applicationId - application id
	 * @param submissionId - submission id
	 * @param dispatchIds - list of dispatch ids
	 */
	private void addDispatchRecordContext(String applicationId, String submissionId, List<String> dispatchIds) {
		final String methodName = SubmissionDispatchHandler.class.toString() + ".addDispatchContext()";
		Log.entry(methodName);
		DispatchRecordContext dispatchRecords = new DispatchRecordContext();
		dispatchRecords.setDispatchIds(dispatchIds);
		dispatchRecords.setApplicationId(applicationId);
		dispatchRecords.setSubmissionId(submissionId);
		RequestContext.get().setValue("DISPATCH", dispatchRecords);
		Log.exit(methodName);
	}
}