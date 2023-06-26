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
 * Object: application__v
 * Trigger API Name: application_product_restrictions__c
 * Author: Todd Taylor
 * Date: 8 June 2017
 *
 * Set product_restriction__c based on values in product_family__c (to drive DAC)
 *
 */
@RecordTriggerInfo(object = "application__v", events = {RecordEvent.BEFORE_INSERT,RecordEvent.BEFORE_UPDATE}, name="application_product_restrictions__c")
public class ApplicationProductRestriction implements RecordTrigger {
	
	private static final String PRODUCT_FAMILY = "product_family__c";
	private static final String PRODUCT_RESTRICTION = "product_restriction__c";
	private static final String PRODUCT_NONE = "none__c";	
	private static final String QUERY_PRODUCT = "select product_restriction__c from product__v where id = {id}";
	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {    
    	final String SINGLE_QUOTE = String.valueOf((char) 39);
    	
        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            try {

                List<String> listProductRestrictions = VaultCollections.newList();

                String productFamily = inputRecord.getNew().getValue(PRODUCT_FAMILY, ValueType.STRING);
                
                boolean bContinue = false;

                /*
                 * 1. Set to none if no product family
                 * 2. Run on insert if product family exists
                 * 3. Run on update if product family changed
                 */
                if (productFamily == null) {
                    listProductRestrictions.add(PRODUCT_NONE);
                    inputRecord.getNew().setValue(PRODUCT_RESTRICTION, listProductRestrictions);
                }
                else if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) {
                    bContinue = true;
                }
                else if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
                	if (!isEqual(productFamily,inputRecord.getOld().getValue(PRODUCT_FAMILY,ValueType.STRING)))
                			bContinue = true;
                	/* commenting out - FLS should make restriction read only
                	if(!isEqual(inputRecord.getOld().getValue(PRODUCT_RESTRICTION, ValueType.PICKLIST_VALUES),
                			inputRecord.getNew().getValue(PRODUCT_RESTRICTION, ValueType.PICKLIST_VALUES)))                     
                    	bContinue = true;
                    */                    
                }
                
                if (bContinue) {
                    // Get the product family name and see if it exists in the picklist                                        
                    String query = QUERY_PRODUCT.replace("{id}", SINGLE_QUOTE + productFamily + SINGLE_QUOTE);
                    
                    QueryService queryService = ServiceLocator.locate(QueryService.class);
                    QueryResponse queryResponse = queryService.query(query);
                    Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
                    while (iterator.hasNext()) {
                        QueryResult queryResult = iterator.next();

                        List<String> listRestrictions = queryResult.getValue(PRODUCT_RESTRICTION, ValueType.PICKLIST_VALUES);
                        
                        if (listRestrictions != null) {
                            for (String s : listRestrictions) {
                                if (s != null && !s.equals("")) listProductRestrictions.add(s);
                            }
                        }

                        if (listProductRestrictions.size() == 0) listProductRestrictions.add(PRODUCT_NONE);

                        inputRecord.getNew().setValue(PRODUCT_RESTRICTION, listProductRestrictions);

                    }
                }
            }
            catch(VaultRuntimeException e) {
                inputRecord.setError("OPERATION_NOT_ALLOWED",
                        e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");

            }
        }
    }
    public static boolean isEqual(Object o1, Object o2) {
    	return o1 == null ? o2 == null :o1.equals(o2);
    }
}