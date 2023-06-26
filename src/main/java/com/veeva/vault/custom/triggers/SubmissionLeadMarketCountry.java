/*
 * --------------------------------------------------------------------
 * Trigger:     SubmissionLeadMarketCountry
 * Object:      submission__v
 * Author:      bryanchan@veeva
 * Created Date:        2019-06-17
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Description: Copy Countries from Application Lead Market field into Submission Countries
 * 
 * Revision:
 * 2019-11-11:  2.5.1 Release: Replaced submission_format__c with dossier_format__v  
 * 2020-04-06:  2.8 Release CR00558: Adding Site Registration and Working Documents to dossier format. 
 * 					Exclude Corporate Dispatch. 
 *
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.util.Log;
import com.veeva.vault.custom.util.QueryServiceUtil;
import com.veeva.vault.custom.util.RecordServiceUtil;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;

import java.util.List;
import java.util.Set;


@RecordTriggerInfo(object = "submission__v", events = {RecordEvent.AFTER_INSERT})
public class SubmissionLeadMarketCountry implements RecordTrigger {	

	// R2.8 - Add Site Registration and Working Documents
	private static final String VQL_DOSSIER_FORMAT ="SELECT id FROM controlled_vocabulary__rim "
			+ "WHERE controlled_vocabulary_type__rim='dossier_format__v' "
			+ "AND name__v CONTAINS ('non-eCTD','Site Registration','Working Documents')";
	
	
	/**
	 * Trigger override for the execute method
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * //com.veeva.vault.sdk.api.data.RecordTrigger#execute(com.veeva.vault.sdk.
	 * api.data.RecordTriggerContext)
	 */
	public void execute(RecordTriggerContext recordTriggerContext) {
		final String methodName = SubmissionLeadMarketCountry.class.toString() + ".execute()";
		Log.entry("----" + methodName + "----");
		
		// Filter out only applicable records (submission = non-ectd)
		Set<String> applicableRecords = retrieveApplicableRecords(recordTriggerContext);
		if (applicableRecords.size() > 0) {
			createSubmissionCountry(applicableRecords);
		}
		Log.exit("----" + methodName + "----");
	}
	
	/**
	 * 1. Find the lead market from the application in the submission
	 * 2. Creates the submission country in batches
	 * 
	 * @param applicableRecords - a set of distinct submission ids, max size is 500 per batch
	 */
	@SuppressWarnings("unchecked")
	private void createSubmissionCountry(Set<String> applicableRecords) {
		final String methodName = SubmissionLeadMarketCountry.class.toString() + ".createSubmissionCountry()";
		Log.entry("----" + methodName + "----");
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		List<Record> recordList = VaultCollections.newList();
		// Batch all the records into one query
		// R2.8 - Exclude Corporate Dispatch Submissions
		final String query = "SELECT id, application__vr.lead_market__rim FROM submission__v "
				+ "WHERE id CONTAINS ('" + String.join("','", applicableRecords) + "') "
						+ "AND object_type__vr.api_name__v!='corporate_dispatch__c'";
		QueryResponse queryResponse = QueryServiceUtil.query(query);
        queryResponse.streamResults().forEach(queryResult -> {
            String submissionId = queryResult.getValue("id",ValueType.STRING);
            String applicationLeadMarket = queryResult.getValue("application__vr.lead_market__rim",ValueType.STRING);
            Record record = recordService.newRecord("submission_country__rim");
            record.setValue("submission__rim", submissionId);
            record.setValue("country__rim", applicationLeadMarket);
            record.setValue("orion_record__c", VaultCollections.asList("no__c"));
            recordList.add(record);
        });
        RecordServiceUtil.updateRecordList(recordList, "Submission Country");
        Log.exit("----" + methodName + "----");
	}
	
	/**
	 * Loops through a list of submissions and collect a list of non-ectd submissions
	 * 
	 * @param recordTriggerContext the record context
	 * @return a distinct set of applicable records
	 */
	@SuppressWarnings({ "unchecked" })
	private Set<String> retrieveApplicableRecords(RecordTriggerContext recordTriggerContext) {
		final String methodName = SubmissionLeadMarketCountry.class.toString() + ".retrieveApplicableRecords()";
		Log.entry("----" + methodName + "----");
		Set<String> applicableRecords = VaultCollections.newSet();
		QueryResponse queryResponse = QueryServiceUtil.query(VQL_DOSSIER_FORMAT);
		// Get the first result id
		//String dossierFormatIds = queryResponse.streamResults().findAll().get().getValue("id",ValueType.STRING);
		queryResponse.streamResults().forEach(queryResult -> {
			String dossierFormatIds = queryResult.getValue("id",ValueType.STRING);
			Log.debug("Dossier value found:" + dossierFormatIds);
			for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
				String recordId = inputRecord.getNew().getValue("id", ValueType.STRING);
				if (isDossierFormatApplicable(inputRecord.getNew(), dossierFormatIds)) {
					Log.debug("Applicable Dossier Format detected.  Adding submission id: " + recordId + " to be processed.");
					applicableRecords.add(recordId);
				} else {
					Log.debug("Submission is not applicable for Dossier Format: " + dossierFormatIds + ".  Skipping submission id: " + recordId);
				}
			}
			Log.exit("----" + methodName + "----, Size:" + applicableRecords.size() );
		});
		
		return applicableRecords;
	}
	
	/**
	 * Checks if a submission dossier format is applicable.  If submission format does not exist, it returns false.
	 * 
	 * @param record - the submission record to validate
	 * @param formatId - the id of the dossier record for controlled vocabulary
	 * @return true if submission format is applicable, false otherwise
	 * 
	 * Notes:
	 * submission_format__c has been deprecated as of R2.5.1.
	 * Using dossier_format__v instead.
	 */
	private boolean isDossierFormatApplicable(Record record, String formatId) {	
		String format = record.getValue("dossier_format__v", ValueType.STRING);
		return (format != null && format.equals(formatId));
	}
	
}