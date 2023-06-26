/*
 * --------------------------------------------------------------------
 * Module:      Create Dispatch Recipients Step
 * Author:      bryanchan @ Veeva
 * Created Date:        2017-12-22
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Description:  Populate MultiAgreement Activity query field
 * 
 * Revision:
 * 2019-02-12: Refactored code to use UDC
 * 2020-05-01: 2.8 Release. Added related object support
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.modules;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.veeva.vault.custom.model.SDKSetting;
import com.veeva.vault.custom.util.BDLUtil;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.custom.util.TriggerUtil;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;

@UserDefinedClassInfo()
public class BDLPopulateMultiAgreementTaskQuery {
	
	private static final String NEW_STATE = "new__c";
	private static final String FIELD_LIFECYCLE_STATE = "state__v";
	private RecordTriggerContext recordTriggerContext;
	private List<SDKSetting> sdkSettings;
		
	public BDLPopulateMultiAgreementTaskQuery(RecordTriggerContext recordTriggerContext, List<SDKSetting> sdkSettings) {
		this.recordTriggerContext = recordTriggerContext;
		this.sdkSettings = sdkSettings;
	}

	public void execute() {
		final String methodName = BDLPopulateMultiAgreementTaskQuery.class.toString() + ".execute()";
		Log.entry("******** " + methodName + " ********");
		
		// Step 1. Retrieve Applicable Records
		Set<RecordChange> applicableRecords = retrieveAllApplicableRecords();
		if (applicableRecords.size() > 0) {
			
			// Step 2. Retrieve Object Type Map from Request Context
			Map<String, String> activityObjectType = BDLUtil.retrieveBDLObjectTypeMap("bdl_multi_agreement_activity__c");
			
			// Step 3. Populate the task query
			populateTaskQuery(applicableRecords, activityObjectType);
			
		}
		
		Log.exit("******** " + methodName + " ********");
	}
	
	private Set<RecordChange> retrieveAllApplicableRecords() {
		final String methodName = BDLPopulateMultiAgreementTaskQuery.class.toString() + ".retrieveApplicableRecords()";
		Log.entry(methodName);
		@SuppressWarnings("unchecked")
		Set<RecordChange> applicableRecords = VaultCollections.newSet();
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) {
				applicableRecords.add(inputRecord);
			} else if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
				String state = inputRecord.getNew().getValue(FIELD_LIFECYCLE_STATE, ValueType.STRING);
				if (state.equals(NEW_STATE)) {
					applicableRecords.add(inputRecord);
				}
				//if (TriggerUtil.fieldChanged(inputRecord, FIELD_LIFECYCLE_STATE, ValueType.STRING, recordTriggerContext.getRecordEvent())) {}
			}
		}
		Log.exit(methodName + ", Applicable Record Size:" + applicableRecords.size());
		return applicableRecords;
	}


	
	private void populateTaskQuery(Set<RecordChange> applicableRecords, Map<String, String> activityObjectType) {
		final String methodName = BDLPopulateMultiAgreementTaskQuery.class.toString() + ".populateTaskQuery()";
		Log.entry(methodName);
		
		for (RecordChange applicableRecord: applicableRecords) {
			SDKSetting setting = BDLUtil.retrieveSDKSetting(sdkSettings, applicableRecord, activityObjectType);
			
			if (setting != null) {
				Log.debug("Setting Found: [" + setting.getName() + "]");
				String criteriaQuery = resolveInputRecordTokens(applicableRecord, setting.getCriteriaVQL());
				applicableRecord.getNew().setValue("long_query__c", criteriaQuery);
			}
		}

		Log.exit(methodName);
	}
	

	
	/**
	 * Takes a triggered record and resolve the tokens
	 * 
	 * @param inputRecord
	 *            - the record being triggered
	 * @param text
	 *            - the entire text with tokens to be resolved
	 * @return the text with all tokens resolved
	 */
	private String resolveInputRecordTokens(RecordChange inputRecord, String text) {
		final String methodName = BDLPopulateMultiAgreementTaskQuery.class.toString() + ".resolveInputRecordTokens()";
		Log.entry(methodName);
		Set<String> tokens = TriggerUtil.retrieveTokensFromText(text);
		for (String token : tokens) {
			// Special Function tokens here:
			// ${PICKLIST-IF-OR[source=target]}
			// Example:
			// ${PICKLIST-IF-OR[rm_partner_version_needed__c|request_data__c:requests_data_from_partner__c=yes__c,review__c:nonprepare_reviews__c=yes__c]}
			if (token.startsWith("PICKLIST-IF-OR[") && token.endsWith("]")) {
				String[] picklistPair = StringUtils.split(token.replace("PICKLIST-IF-OR[", "").replace("]", ""), "\\|");
				if (picklistPair.length == 2) {
					String fieldToken = picklistPair[0];
					String[] fieldCondition = StringUtils.split(picklistPair[1], ",");
					List<String> tokenValues = (List<String>) inputRecord.getNew().getValue(fieldToken, ValueType.PICKLIST_VALUES);

					if (tokenValues.size() > 0) {
						StringBuilder orClause = new StringBuilder("(");
						for (int i = 0; i < tokenValues.size(); i++) {
							for (String ifStatement : fieldCondition) {
								String[] ifCondition = StringUtils.split(ifStatement, ":");
								if (tokenValues.get(i).equals(ifCondition[0])) {
									if (i == 0) {
										orClause.append(ifCondition[1]);
									} else {
										orClause.append(" OR " + ifCondition[1]);
									}
								}
							}
						}
						orClause.append(")");
						if (orClause.length() > 2) {
							text = text.replace("${" + token + "}", orClause.toString());
						} else {
							inputRecord.setError("OPERATION_NOT_ALLOWED","Task Query with field " + fieldToken + "does not resolve to a valid token value. ");
						}
					} else {
						text = text.replace("${" + token + "}", fieldToken + "=null");
					}
				}

			// Special Function tokens here:
			// ${PICKLIST-OR[source=target]}
			// Example:
			// ${PICKLIST-OR[aggregate_report_type__c=report_type__c]}
			} else if (token.startsWith("PICKLIST-OR[") && token.endsWith("]")) {
				String[] picklistPair = StringUtils.split(token.replace("PICKLIST-OR[", "").replace("]", ""), "=");
				if (picklistPair.length == 2) {
					String source = picklistPair[0];
					String target = picklistPair[1];
					List<String> tokenValues = (List<String>) inputRecord.getNew().getValue(source, ValueType.PICKLIST_VALUES);

					if (tokenValues.size() > 0) {
						StringBuilder orClause = new StringBuilder("(");
						for (int i = 0; i < tokenValues.size(); i++) {
							if (i == 0) {
								orClause.append(target + "=" + "'" + tokenValues.get(i) + "'");
							} else {
								orClause.append(" OR " + target + "=" + "'" + tokenValues.get(i) + "'");
							}
						}
						orClause.append(")");
						if (orClause.length() > 2) {
							text = text.replace("${" + token + "}", orClause.toString());
						} else {
							inputRecord.setError("OPERATION_NOT_ALLOWED","Task Query with field " + source + " does not resolve to a valid token value. ");
						}
					} else {
						text = text.replace("${" + token + "}", target + "=null");
					}
				}
			} else if (token.startsWith("PICKLIST[") && token.endsWith("]")) {
				token = token.replace("PICKLIST[", "").replace("]", "");
				List<String> tokenValue = inputRecord.getNew().getValue(token, ValueType.PICKLIST_VALUES);
				text = StringUtils.replaceAll(text, "\\$\\{PICKLIST\\[" + token + "\\]\\}", "'" + String.join("','", tokenValue) + "'");
				Log.debug("Picklist Conversion Result: " + text );
			// 2.8 Release - ignore the . token.  This will be handled in the actual generation of task.
			} else if (token.equals("DISTINCT") || token.contains(".")) { 
				// do nothing			
			} else if (token.equals("bdl_multi_agreement_countries__c")) {
				// 343: Add multi-country picklist to filtering.  Convert picklist to Country object id
				// The value must be the country object id. 
				List<String> countries = inputRecord.getNew().getValue("bdl_multi_agreement_countries__c", ValueType.PICKLIST_VALUES);		
				for (int i = 0; i < countries.size(); i++) {
					// countries picklist uses id value: 000sc0d000012__c.  Transform it to 000SC0D000012
					countries.set(i, countries.get(i).substring(0, countries.get(i).length()-3).toUpperCase());
				}
				text = StringUtils.replaceAll(text, "\\$\\{bdl_multi_agreement_countries__c\\}", "('" + String.join("','", countries) + "')");
			} else {
				String tokenValue = inputRecord.getNew().getValue(token, ValueType.STRING);
				//text = text.replaceAll("\\$\\{" + token + "\\}", "'" + tokenValue + "'");
				text = StringUtils.replaceAll(text, "\\$\\{" + token + "\\}", "'" + tokenValue + "'");
			}
		}
		Log.exit(methodName);
		return text;
	}
	
}