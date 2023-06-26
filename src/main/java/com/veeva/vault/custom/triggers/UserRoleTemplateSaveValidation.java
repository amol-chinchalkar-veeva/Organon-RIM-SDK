package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.BatchOperationError;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;
import com.veeva.vault.sdk.api.query.QueryCountRequest;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Object: user_role_template__c
 * Trigger API Name: user_role_template_save_validation__c
 * Author: Todd Taylor
 * Date: 27 July 2017
 *
 * Save rules for template admin if there are existing users provisioned
 * to the template group
 * 1. Insert - cannot insert as active (needed to ensure subsequent triggers and read records have generated ids to query)
 * 2. Delete - cannot delete active records (must inactivate first, then delete)
 * 
 */
@RecordTriggerInfo(object = "user_role_template__c", events = {RecordEvent.BEFORE_INSERT,RecordEvent.BEFORE_DELETE}, order = TriggerOrder.NUMBER_1, name="user_role_template_save_validation__c")
public class UserRoleTemplateSaveValidation implements RecordTrigger  {

	private static final String ERROR_INSERT = "Existing User Role Provisioning records exist for this Template Group. New records must be created as inactive, then activated. This ensures proper re-processing of all users in the Template Group."; 
	private static final String ERROR_DELETE = "Existing User Role Provisioning records exist for this Template Group. Active template records cannot be deleted. Please inactivate the record, then delete.";
	
    public void execute(RecordTriggerContext recordTriggerContext)  {    	
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			
			try {     
				String SINGLE_QUOTE = String.valueOf((char) 39);
				
				boolean bExistingRecords = false;
				String templateGroup = "";
				String status = "";
				
				if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_DELETE) {
					templateGroup = inputRecord.getOld().getValue("template_group__c", ValueType.STRING);
					List<String> listStatus = inputRecord.getOld().getValue("status__v", ValueType.PICKLIST_VALUES);
					status = listStatus.get(0);	    			 	
				}
				else { 
					templateGroup = inputRecord.getNew().getValue("template_group__c", ValueType.STRING);
					List<String> lCurrent = inputRecord.getNew().getValue("status__v", ValueType.PICKLIST_VALUES);
					status = lCurrent.get(0);
					
				}
	    		String query = "select id from user_role_template_assignment__c where template_group__c = " + SINGLE_QUOTE + templateGroup + SINGLE_QUOTE + " and status__v=" + SINGLE_QUOTE + "active__v" + SINGLE_QUOTE;
				query += " and user__cr.status__v = 'active__v'";
	    		query += " LIMIT 1";

	    		/*QueryService queryService = ServiceLocator.locate(QueryService.class);
	    		QueryResponse queryResponse = queryService.query(query);
	    		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
	
	    		while (iterator.hasNext()) {
	                QueryResult qr = iterator.next();     
	                bExistingRecords = true;
	    		}*/

				long queryResultCount = getQueryResultCount(query);
				bExistingRecords = queryResultCount > 0;

	    		if (bExistingRecords && status.equals("active__v")) {
	    			if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) {
	    				inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_INSERT);
	    			}
	    			
	    			if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_DELETE) {
	    				inputRecord.setError("OPERATION_NOT_ALLOWED",ERROR_DELETE);
	    			}	    			
	    		}				
			}
			catch(VaultRuntimeException e) {
				inputRecord.setError("OPERATION_NOT_ALLOWED",
                        e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
           }
		}    		     	   		
    }

	public long getQueryResultCount(String query) {
		QueryService queryService = ServiceLocator.locate(QueryService.class);
		QueryCountRequest queryCountRequest = queryService
				.newQueryCountRequestBuilder()
				.withQueryString(query)
				.build();

		final long[] queryResultCount = {0};

		queryService.count(queryCountRequest)
				.onSuccess(queryCountResponse -> {
					queryResultCount[0] = queryCountResponse.getTotalCount();
				})
				.onError(queryOperationError -> {
					//String err = queryOperationError.getMessage();
				})
				.execute();

		return queryResultCount[0];
	}
}