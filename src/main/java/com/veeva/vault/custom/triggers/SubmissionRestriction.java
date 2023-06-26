package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Object: submission__v
 * Trigger API Name: submission_restriction__c
 * Author: Todd Taylor
 * Date: 8 June 2017
 *
 * Set submission_type_restriction__c using three fields:
 * 1. Submission Type
 * 2. Application Type
 * - will be temporarily populated in sdk_application_type__c if update comes from application
 * - query if blank (update is from submission or SA restricted type)
 * 
 * 3. SA Restricted Type Setup Data
 * - will be temporarily populated in sdk_sa_restricted_type__c if update comes from setup object 
 * - query if blank (update is from submission or application)
 *
 * Usage of the temporary fields allows rules to be centralized within this trigger. It also assists
 * hurdles with cross object updates if data is changing in multiple areas.
 *
 * Rules for setting the Submission Type Restriction:
 * If the restriction exists, and the Submission State is Final (final_state__c), set Submission Type Restriction = Restricted (restricted__c)
 * If the restriction does not exist, and the Submission State is Final (final_state__c), set Submission Type Restriction = Unrestricted (nonrestricted__c)
 * If the restriction exists, and the Submission State is not Final, set Submission Type Restriction = Restricted Draft (restricted_draft__c)
 * If the restriction does not exist, and the Submission State is not Final, set Submission Type Restriction = Unrestricted - Draft (nonrestricted_draft__c) 
 *
 * Updated Rules 12-08-17 (Rich Pater)
 * If the restriction exists, and the Submission State is Final (final_state__c), set Submission Type Restriction = Value of Restriction Type (restriction_type__c) on SA Restricted Submission Type object
 * If the restriction does not exist, and the Submission State is Final (final_state__c), set Submission Type Restriction = Unrestricted (nonrestricted__c)
 * If the restriction exists, and the Submission State is not Final, set Submission Type Restriction = Value of Restricted Type - Draft (restricted_type_draft__c) on SA Restricted Submission Type object
 * If the restriction does not exist, and the Submission State is not Final, set Submission Type Restriction = Unrestricted - Draft (nonrestricted_draft__c)
 * 
 */
@RecordTriggerInfo(object = "submission__v", events = {RecordEvent.BEFORE_INSERT,RecordEvent.BEFORE_UPDATE}, name="submission_restriction__c")
public class SubmissionRestriction implements RecordTrigger {
	
	private static final String SUBMISSION_TYPE_RESTRICTION = "submission_type_restriction__c";
	
	// submission_type_restriction__c picklist values
	private static final String RESTRICTED = "restricted__c";
	private static final String NONRESTRICTED = "nonrestricted__c";
	private static final String RESTRICTED_DRAFT = "restricted_draft__c";
	private static final String NONRESTRICTED_DRAFT = "nonrestricted_draft__c";
	private static final String SAFETY = "safety__c";
	private static final String SAFETY_DRAFT = "safety_draft__c";
	
	private static final String RESTRICTION_TYPE = "restriction_type__c";
	private static final String RESTRICTION_TYPE_DRAFT = "restricted_type_draft__c";
	
	private static final String SDK_APPLICATION_TYPE = "sdk_application_type__c";
	private static final String SDK_RESTRICTED_TYPE = "sdk_sa_restricted_type__c";
	private static final String SDK_RESTRICTED_TYPE_YES = "yes"; // use yes/no text values instead of boolean 
	private static final String SDK_RESTRICTED_TYPE_NO = "no";
	
	private static final String LIFECYCLE_STATE_FINAL = "final_state__c"; 
	private static final String LIFECYCLE_STATE_ASSEMBLY = "in_assembly_review_state__c";

	private static final int BATCH_SIZE = 500;
	
    public void execute(RecordTriggerContext recordTriggerContext) {  
    	final String SINGLE_QUOTE = String.valueOf((char) 39);

        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
            try {
                List<String> listProductRestrictions = VaultCollections.newList();
                
				String submissionType = inputRecord.getNew().getValue("submission_type__rim", ValueType.STRING);
                String applicationType = inputRecord.getNew().getValue(SDK_APPLICATION_TYPE, ValueType.STRING);
                String saRestrictedType = inputRecord.getNew().getValue(SDK_RESTRICTED_TYPE, ValueType.STRING);
                /* 
                 * Criteria for running trigger logic:
                 * 1. Before insert (always - submission type is a required field)
                 * Before Update:
                 * 2. Submission Type has changed or State has changed
                 * 3. sdk_application_type__c is non null (value populated from application trigger)
                 * 4. sdk_sa_restricted_type__c is non null (value populated from sa restricted type trigger)
                 * 5. if the restriction type (submission_type_restriction__c) has changed from a direct edit 
                 * 
                 */
                boolean bContinue = false;
                
                String stateNew = inputRecord.getNew().getValue("state__v", ValueType.STRING);
                
                if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) bContinue = true;
                else if (!submissionType.equals(inputRecord.getOld().getValue("submission_type__rim", ValueType.STRING))) bContinue = true;
                else if (!stateNew.equals(inputRecord.getOld().getValue("state__v", ValueType.STRING))) bContinue = true;
                else if (applicationType != null && !applicationType.equals("")) bContinue = true;
                else if (saRestrictedType != null && !saRestrictedType.equals("")) bContinue = true;
                else if (!isEqual(inputRecord.getNew().getValue(SUBMISSION_TYPE_RESTRICTION, ValueType.PICKLIST_VALUES),inputRecord.getOld().getValue(SUBMISSION_TYPE_RESTRICTION, ValueType.PICKLIST_VALUES))) bContinue = true;
                
                if (bContinue) {
                	
                	QueryService queryService = ServiceLocator.locate(QueryService.class);
                    QueryResponse queryResponse = null;
                    Iterator<QueryResult> iterator = null;
                    String query = "";
                    
                    // APPLICATION TYPE - query/retrieve the application type if the value is null
                	if (applicationType == null || applicationType.equals("")) {
	                    query = "select application_type__rim from application__v where id = "+ SINGLE_QUOTE + inputRecord.getNew().getValue("application__v", ValueType.STRING) + SINGLE_QUOTE;
	
	                    queryService = ServiceLocator.locate(QueryService.class);
	                    queryResponse = queryService.query(query);
	                    iterator = queryResponse.streamResults().iterator();
	
	                    while (iterator.hasNext()) {
	                        QueryResult queryResult = iterator.next();
	                        applicationType = queryResult.getValue("application_type__rim", ValueType.STRING);
	                    }
                	}
                	
                	// RESTRICTION - query/retrieve the restrictions if the value is null
                	boolean restrictionExists = false;
                	String restrictionType = "";
                	String restrictionTypeDraft = "";
                	
                	if (saRestrictedType == null || saRestrictedType.equals("") || saRestrictedType.equalsIgnoreCase("yes")) {
	                    query = "select id,"+RESTRICTION_TYPE+","+RESTRICTION_TYPE_DRAFT+" from sa_restricted_submission_type__c where status__v=" + SINGLE_QUOTE + "active__v" + SINGLE_QUOTE;
	                    query += " and submission_type__c=  " + SINGLE_QUOTE + submissionType + SINGLE_QUOTE;
	                    query += " and application_type__c=  " + SINGLE_QUOTE + applicationType + SINGLE_QUOTE;
	
	                    queryResponse = queryService.query(query);
	                    iterator = queryResponse.streamResults().iterator();
	
	                    while (iterator.hasNext()) {
	                        QueryResult queryResult = iterator.next();
	                        restrictionType = (String) queryResult.getValue(RESTRICTION_TYPE, ValueType.PICKLIST_VALUES).get(0);
	                        restrictionTypeDraft = (String) queryResult.getValue(RESTRICTION_TYPE_DRAFT, ValueType.PICKLIST_VALUES).get(0);
	                        restrictionExists = true;
	                    }
                	}
                	else if (!saRestrictedType.equalsIgnoreCase("no")) {
                		restrictionExists = true;
                		//Pass the passthrough value as both, the state has already been accounted for
                		restrictionType = saRestrictedType;
                		restrictionTypeDraft = saRestrictedType;
                	}
                	//else {
                		//RP: Commented out, moved condition of restricted type equals yes above to still query for the actual values of restriction type
                		// restriction sdk_sa_restricted_type__c is populated, transform from yes/no to boolean
                		//if (saRestrictedType.equalsIgnoreCase("yes")) restrictionExists = true;
                	//}
                	
                	// DETERMINE THE RESTRICTED TYPE VALUE
                    String sRestriction = processRestriction(stateNew, restrictionExists, restrictionType, restrictionTypeDraft);

                    if (!sRestriction.equalsIgnoreCase("")) {
                        List<String> listRestrict = VaultCollections.newList();
                        listRestrict.add(sRestriction);

                        inputRecord.getNew().setValue(SUBMISSION_TYPE_RESTRICTION, listRestrict);
                    }

                    // Clear out the temp field for next save
                    inputRecord.getNew().setValue(SDK_APPLICATION_TYPE, null);
                    inputRecord.getNew().setValue(SDK_RESTRICTED_TYPE, null);
                }
            }
            catch(VaultRuntimeException e) {
                inputRecord.setError("OPERATION_NOT_ALLOWED",
                        e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");

            }
        }
    }

    /**
     * Restricted type processing rules.<br><br>
     * If the restriction exists, and the Submission State is Final (final_state__c), set Submission Type Restriction = Restricted (restricted__c)<br><br>
     * If the restriction does not exist, and the Submission State is Final (final_state__c), set Submission Type Restriction = Unrestricted (nonrestricted__c)<br><br>
     * If the restriction exists, and the Submission State is not Final, set Submission Type Restriction = Restricted Draft (restricted_draft__c)<br><br>
     * If the restriction does not exist, and the Submission State is not Final, set Submission Type Restriction = Unrestricted - Draft (nonrestricted_draft__c)<br> 
     *
     * @param submissionState
     *   - Submission lifecycle state
     * @param restrictionExists
     *   - Boolean for whether a restriction exists
     * @return String
     *   - Evaluated restriction value
     */
    private String processRestriction(String submissionState, boolean restrictionExists, String restrictionType, String restrictionTypeDraft) {
        
        String s = "";
        boolean stateFinal = false;

        if (submissionState != null && submissionState.equalsIgnoreCase(LIFECYCLE_STATE_FINAL)) stateFinal = true;
        
        /* Change request to also include assembly state */
        if (submissionState != null && submissionState.equalsIgnoreCase(LIFECYCLE_STATE_ASSEMBLY)) stateFinal = true;
        
        if (restrictionExists && stateFinal)
            s = restrictionType;
        	//s = RESTRICTED;
        else if (restrictionExists && !stateFinal)
        	s = restrictionTypeDraft;
        	//s = RESTRICTED_DRAFT;
        else if (!restrictionExists && stateFinal)
            s = NONRESTRICTED;
        else if (!restrictionExists && !stateFinal)
            s = NONRESTRICTED_DRAFT;
        
        return s;
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
    
}