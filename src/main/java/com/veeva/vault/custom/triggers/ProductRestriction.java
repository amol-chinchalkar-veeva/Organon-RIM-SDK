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

/**
 * Object: product__v
 * Trigger API Name: product_restrictions__c
 * Author: Todd Taylor
 * Date: 14 July 2017
 *
 * If the product restriction is added/changed, update the related application.
 * Query all applications with the old value. Update them to the new value.
 *
 */
@RecordTriggerInfo(object = "product__v", events = {RecordEvent.AFTER_INSERT,RecordEvent.AFTER_UPDATE}, name="product_restrictions__c")
public class ProductRestriction implements RecordTrigger {
	
	private static final String PRODUCT_FAMILY = "product_family__c";
	private static final String PRODUCT_RESTRICTION = "product_restriction__c";
	private static final String QUERY_APPLICATION = "select id,product_restriction__c from application__v where product_family__c = {product_family__c}";
	private static final String PRODUCT_NONE = "none__c";	
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	final String SINGLE_QUOTE = String.valueOf((char) 39);
    	List<Record> listRecord = VaultCollections.newList();

    	try {
    		 for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
                
            	String prodRestrictionNew = "";
                
                List<String> listRestrict = inputRecord.getNew().getValue(PRODUCT_RESTRICTION, ValueType.PICKLIST_VALUES);
                if (listRestrict != null) prodRestrictionNew = listRestrict.get(0);

                boolean bContinue = false;
                
                if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_INSERT) {
                	if (!prodRestrictionNew.equals("")) bContinue = true;                			
                }
                else {
                	String prodRestrictionOld = "";
                	listRestrict = inputRecord.getOld().getValue(PRODUCT_RESTRICTION, ValueType.PICKLIST_VALUES);
                	if (listRestrict != null) prodRestrictionOld = listRestrict.get(0);	 
                	
                	if (!prodRestrictionNew.equals(prodRestrictionOld)) bContinue = true;
                }
                                
                if (bContinue) {
                	
                	List<String> listRestrictions = VaultCollections.newList();
                	if (prodRestrictionNew.equals("")) listRestrictions.add(PRODUCT_NONE);
                	else listRestrictions.add(prodRestrictionNew);
                	
                    String query = QUERY_APPLICATION.replace("{product_family__c}", SINGLE_QUOTE + inputRecord.getNew().getValue("id", ValueType.STRING) + SINGLE_QUOTE);
                    
                    QueryService queryService = ServiceLocator.locate(QueryService.class);
                    QueryResponse queryResponse = queryService.query(query);
                    Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
                    
                    RecordService recordService = ServiceLocator.locate(RecordService.class);
                   
                    
                    while (iterator.hasNext()) {
                        QueryResult queryResult = iterator.next();

                        Record r = recordService.newRecord("application__v");
                        
                        r.setValue("id", queryResult.getValue("id", ValueType.STRING));
                        r.setValue(PRODUCT_RESTRICTION, listRestrictions);

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