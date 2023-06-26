/*
 * --------------------------------------------------------------------
 * Trigger: UserRoleTemplateAssignmentProvision
 * Author: Todd Taylor
 * Created Date:        2017-07-11
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:
 * Dynamically populate DAC user_role_setup__v or a customer defined user
 *   role setup object. All logic is driven by setup data in:
 * - User Role Template Groups
 * - User Role Templates
 * - User Role Template Mappings
 * - User Role Provisioning
 *---------------------------------------------------------------------
 * Revision:
 * 2020-07-11: Initial Release
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved inner class to it's own UDC.
 *    Moved Delete Query to UDS to solve memory issue. See VpsUserRoleTemplateProvisionService
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.custom.services.VpsUserRoleTemplateGroupService;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;

import java.util.*;

@RecordTriggerInfo(object = "user_role_template_assignment__c", events = {RecordEvent.AFTER_INSERT,RecordEvent.AFTER_UPDATE}, name="user_role_template_assignment_provision__c")
public class UserRoleTemplateAssignmentProvision implements RecordTrigger {

	// CONSTANTS
	private static final String ERROR_USER_CHANGE = "Cannot change users on existing records. If changing users, please remove/inactivate the current user and add a record for the new user.";
	private static final String ERROR_COUNTRY_CHANGE = "Cannot change country on existing records. To change country, please remove/inactivate the current user and add a record for the new user/country.";
	private static final String ERROR_COUNTRY = "Invalid data - Country cannot be specified if the Country is not defined on the Template Group. Please review the Template Group setup data.";
	private static final String ERROR_COUNTRY_REGION = "Save Error - Cannot select both Country and Region.";
	private static final String TOKEN_DUPLICATE_RECORD = "duplicate record";
	private static final int BATCH_SIZE = 500;

	// URS volume is speculative, this constant optimizes commits versus buffer memory and max 10k Vault Collection size
	private static final int BATCH_USER_SIZE = 500;

	public void execute(RecordTriggerContext recordTriggerContext) {
		try {
			LogService debug = ServiceLocator.locate(LogService.class);
			/*
			 * Loop #1 through trigger context
			 * 1. Perform save validations
			 * 2. Initialize setup data - grab all template groups for the current batch
			 */
			Map<String, TemplateGroup> mapTemplates = VaultCollections.newMap();

			VpsUserRoleTemplateGroupService vpsUserRoleTemplateGroupService = ServiceLocator.locate(VpsUserRoleTemplateGroupService.class);

			for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
				String templateGroupId = "";
				List<String> listURS = VaultCollections.newList();

				if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) {
					templateGroupId = inputRecord.getOld().getValue("template_group__c", ValueType.STRING);
					listURS = inputRecord.getOld().getValue("user_role_setup_object__c", ValueType.PICKLIST_VALUES);
				}
				else {
					if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_UPDATE) {
						if (!inputRecord.getNew().getValue("user__c", ValueType.STRING).equals(inputRecord.getOld().getValue("user__c", ValueType.STRING))) {
							RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", ERROR_USER_CHANGE);

							throw rollbackException;
						}
					}
					if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_UPDATE) {
						String oldCountry = inputRecord.getOld().getValue("country__c", ValueType.STRING);
						String newCountry = inputRecord.getNew().getValue("country__c", ValueType.STRING);

						if ((oldCountry == null && newCountry != null) ||
								(oldCountry != null && newCountry == null) ||
								(oldCountry != null && newCountry != null && !oldCountry.equals(newCountry))) {

							RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", ERROR_COUNTRY_CHANGE);

							throw rollbackException;
						}
					}
					templateGroupId = inputRecord.getNew().getValue("template_group__c", ValueType.STRING);
					listURS = inputRecord.getNew().getValue("user_role_setup_object__c", ValueType.PICKLIST_VALUES);
				}
				mapTemplates.put(templateGroupId, new TemplateGroup(templateGroupId,listURS));

			}

    		/*for (String s : mapTemplates.keySet()) {
    			//TemplateGroup template = mapTemplates.get(s);
    			vpsUserRoleTemplateGroupService.initTemplateGroup(mapTemplates.get(s));
    		}*/


			/*
			 * Loop #2 through trigger context. The Setup data is ready,
			 * process the template assignment records.
			 *
			 * - handle deletes or inactivations (update)
			 * - handle inserts or updates (if active)
			 *
			 * Use two variables because an update with both active/inactive
			 * can result in inserts and deletes
			 */
			RecordService recordService = ServiceLocator.locate(RecordService.class);
			List<Record> listRecordsToAdd = VaultCollections.newList();
			List<Record> listRecordsToDelete = VaultCollections.newList();

			for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
				boolean inactivation = false;

				if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_UPDATE) {
					List<String> lCurrent = inputRecord.getNew().getValue("status__v", ValueType.PICKLIST_VALUES);

					if (lCurrent.get(0).equals("inactive__v"))
						inactivation = true;
				}

//    			if (inactivation || recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) {
//    				TemplateGroup templateGroup = mapTemplates.get(inputRecord.getOld().getValue("template_group__c", ValueType.STRING));
//
//					vpsUserRoleTemplateGroupService.initTemplateGroup(templateGroup);
//
//    				List<Record> listRecordDelete = VaultCollections.newList();
//		    		listRecordDelete = vpsUserRoleTemplateGroupService.getExistingURSRecords(templateGroup,
//							inputRecord.getOld().getValue("user__c", ValueType.STRING),
//							inputRecord.getOld().getValue("country__c",ValueType.STRING),
//							inputRecord.getOld().getValue("country_api_name__c", ValueType.STRING));
//
//		    		if (listRecordDelete != null && listRecordDelete.size() >0)
//		    			listRecordsToDelete.addAll(listRecordDelete);
//
//		    		debug.debug("listRecordsToDelete size " + listRecordsToDelete.size());
//    			}
				else {
					// INSERT or UPDATE (ACTIVE)
					TemplateGroup templateGroup = mapTemplates.get(inputRecord.getNew().getValue("template_group__c", ValueType.STRING));

					vpsUserRoleTemplateGroupService.initTemplateGroup(templateGroup);

					// throw exception if a country was populated but not in the setup data
					String newCountry = inputRecord.getNew().getValue("country__c", ValueType.STRING);
					String countryAPIName = inputRecord.getNew().getValue("country_api_name__c", ValueType.STRING);

					if (newCountry != null && !newCountry.equals("") && (countryAPIName == null || countryAPIName.equals(""))) {
						RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", ERROR_COUNTRY);

						throw rollbackException;
					}

					List<Record> listRecordNew = VaultCollections.newList();
					listRecordNew = vpsUserRoleTemplateGroupService.getNewUserRecords(templateGroup, inputRecord.getNew());
					if (listRecordNew != null && listRecordNew.size() >0)
						listRecordsToAdd.addAll(listRecordNew);
				}
				/*
				 *  Commit every 5k (BATCH_USER_SIZE) or more records. Data volume will be speculative as its
				 *  # of template assignment records x # URS records per template. This optimizes the commits
				 *  in the save records method at 500 (BATCH_SIZE) and stays under the max VaultCollections
				 *  value of 10k.
				 */
				if (listRecordsToAdd.size() >= BATCH_USER_SIZE) {
					saveRecordsBatch(listRecordsToAdd, false);
					listRecordsToAdd = VaultCollections.newList();
				}
//	    		if (listRecordsToDelete.size() >= BATCH_USER_SIZE) {
//	    			saveRecordsBatch(listRecordsToDelete, true);
//	    			listRecordsToDelete = VaultCollections.newList();
//	    		}

			}

			// Commit last of the batch
			if (listRecordsToAdd.size() >= 0)
				saveRecordsBatch(listRecordsToAdd, false);

//    		if (listRecordsToDelete.size() >= 0)
//    			saveRecordsBatch(listRecordsToDelete, true);
		}
		catch(VaultRuntimeException e) {
			RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED",
					e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
			throw rollbackException;
		}
	}

	/**
	 * Perform a DML save operation on a list of records of any size,
	 * properly handling batch size for each commit.
	 *
	 * @param listRecord
	 *          - list of records to perform the save upon
	 *
	 * @param isDelete
	 * 			- call a delete (if false) or save (if true)
	 */
	private void saveRecordsBatch(List<Record> listRecord, boolean isDelete) {
		RecordService recordService = ServiceLocator.locate(RecordService.class);

		LogService debug = ServiceLocator.locate(LogService.class);
		debug.debug("saveRecordsBatch checkpoint");

		/*
		 * Records can belong to multiple objects. Loop through the main list and:
		 * 1. Group records by object name
		 * 2. Commit every 500 (BATCH_SIZE)
		 */
		Map<String, List<Record>> mapRecords = VaultCollections.newMap();
		List<Record> listTemp = VaultCollections.newList(); // temp variable for batching up the list

		for (Record r : listRecord) {
			listTemp = VaultCollections.newList();

			if (mapRecords.containsKey(r.getObjectName()))
				listTemp = mapRecords.get(r.getObjectName());

			listTemp.add(r);

			if (listTemp.size() == BATCH_SIZE) {
				if (isDelete)
					deleteRecords(listTemp);
				else
					saveRecords(listTemp);

				mapRecords.remove(r.getObjectName());
			}
			else
				mapRecords.put(r.getObjectName(), listTemp);
		}

		// handle any remaining records outside the batch (always below BATCH_SIZE)
		if (mapRecords != null && mapRecords.size() > 0) {
			for (String s : mapRecords.keySet()) {
				listTemp = mapRecords.get(s);

				if (listTemp != null && listTemp.size() > 0) {
					if (isDelete)
						deleteRecords(listTemp);
					else
						saveRecords(listTemp);
				}
			}
		}
		
		
		/*
		int idx = 0;
		
		do {
			int iUpper = idx + BATCH_SIZE;
			
			if (iUpper > listRecord.size()) 
				iUpper = listRecord.size(); // handle the remaining records in last batch
			
			if (isDelete)
				deleteRecords(listRecord.subList(idx, iUpper));
			else
				saveRecords(listRecord.subList(idx, iUpper));
			
			idx = iUpper;
			
		} while (idx < listRecord.size());
		
		*/
	}

	/**
	 * Perform a DML save operation on a list of records.
	 *
	 * Rollback the entire transaction if encountering errors
	 * that are not related to a duplicate records.
	 *
	 * @param listRecord
	 *            - list of records to perform the save upon
	 */
	private void saveRecords(List<Record> listRecord) {
		LogService debug = ServiceLocator.locate(LogService.class);
		debug.debug(listRecord.get(0).getObjectName() + " saveRecords size " + listRecord.size());

		RecordService recordService = ServiceLocator.locate(RecordService.class);
		recordService.batchSaveRecords(listRecord)
				.onErrors(batchOperationErrors -> {
					batchOperationErrors.stream().findFirst().ifPresent(error -> {
						String errMsg = error.getError().getMessage();
						if (!errMsg.contains(TOKEN_DUPLICATE_RECORD))
							throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
					});
				})
				.execute();
	}

	/**
	 * Perform a DML save operation on a list of records.
	 * Rollback the entire transaction when encountering errors.
	 *
	 * @param listRecord
	 *            - list of records to delete
	 */
	private void deleteRecords(List<Record> listRecord) {
		LogService debug = ServiceLocator.locate(LogService.class);
		debug.debug("deleteRecords size " + listRecord.size());

		RecordService recordService = ServiceLocator.locate(RecordService.class);
		recordService.batchDeleteRecords(listRecord)
				.onErrors(batchOperationErrors -> {
					batchOperationErrors.stream().findFirst().ifPresent(error -> {
						String errMsg = error.getError().getMessage();
						throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
					});
				})
				.execute();
	}
}