/*
 * --------------------------------------------------------------------
 * Module:      Create Dispatch Recipients Step
 * Author:      bryanchan @ Veeva
 * Created Date:        2017-12-22
 * Last Modifed Date:   2020-05-01
 *---------------------------------------------------------------------
 * Description:  Generates MultiAgreementTask based on a query
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.veeva.vault.custom.model.SDKSetting;
import com.veeva.vault.custom.util.BDLUtil;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.custom.util.QueryServiceUtil;
import com.veeva.vault.custom.util.RecordServiceUtil;
import com.veeva.vault.custom.util.TriggerUtil;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;

@UserDefinedClassInfo()
public class BDLGenerateMultiAgreementTask {
	
	private static final String TASK_GENERATED_STATE = "tasks_generated_state__c";
	private static final String FIELD_LIFECYCLE_STATE = "state__v";
	private static final int MAX_TASK_GENERATION_LIMIT = 5000;
	private static final String TASK_OBJECT_NAME = "multi_agreement_tasks__c";
	
	private RecordTriggerContext recordTriggerContext;
	private List<SDKSetting> sdkSettings;
		
	public BDLGenerateMultiAgreementTask(RecordTriggerContext recordTriggerContext, List<SDKSetting> sdkSettings) {
		this.recordTriggerContext = recordTriggerContext;
		this.sdkSettings = sdkSettings;
	}
	
	public void execute() {
		final String methodName = BDLGenerateMultiAgreementTask.class.toString() + ".execute()";
		Log.entry("******** " + methodName + " ********");
		// Step 1. Retrieve Applicable Records
		Set<RecordChange> applicableRecords = retrieveAllApplicableRecords(recordTriggerContext);
		if (applicableRecords.size() > 0) {
			
			// Step 2. Retrieve Object Type Map from Request Context
			Map<String, String> activityObjectType = BDLUtil.retrieveBDLObjectTypeMap("bdl_multi_agreement_activity__c");
			Map<String, String> taskObjectType = BDLUtil.retrieveBDLObjectTypeMap("multi_agreement_tasks__c");
			
			// Step 3. Execute the query and generate task
			for (RecordChange applicableRecord:applicableRecords) {
				// Generate Task
				executeTaskQuery(applicableRecord, activityObjectType, taskObjectType);
			}
			 
		}
		
		Log.exit("******** " + methodName + " ********");
	}
	
	private Set<RecordChange> retrieveAllApplicableRecords(RecordTriggerContext recordTriggerContext) {
		final String methodName = BDLGenerateMultiAgreementTask.class.toString() + ".retrieveApplicableRecords()";
		Log.entry(methodName);
		@SuppressWarnings("unchecked")
		Set<RecordChange> applicableRecords = VaultCollections.newSet();
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
				if (TriggerUtil.fieldChanged(inputRecord, FIELD_LIFECYCLE_STATE, ValueType.STRING, recordTriggerContext.getRecordEvent())) {
					String state = inputRecord.getNew().getValue(FIELD_LIFECYCLE_STATE, ValueType.STRING);
					if (state.equals(TASK_GENERATED_STATE)) {
						applicableRecords.add(inputRecord);
					}
				}
			}
		}
		Log.exit(methodName + ", Applicable Record Size:" + applicableRecords.size());
		return applicableRecords;
	}

	/**
	 * Executes the task query from a multi-agreement activity
	 * 
	 * @param queryService
	 *            the query service
	 * @param inputRecord
	 *            the trigger input record
	 */
	private void executeTaskQuery(RecordChange inputRecord, Map<String, String> activityObjectType, Map<String, String> taskObjectType) {

		String taskQuery = inputRecord.getNew().getValue("long_query__c", ValueType.STRING);
		String objectTypeId = inputRecord.getNew().getValue("object_type__v", ValueType.STRING);
		String objectType = activityObjectType.get(objectTypeId);
		SDKSetting sdkSetting = BDLUtil.retrieveSDKSetting(sdkSettings, inputRecord, activityObjectType);
		
		if (taskQuery != null && sdkSetting != null) {			
			int numOfTaskCreated = 0;
			
			// 2.8.1 Release: Resolve joined relationship tokens
			taskQuery = resolveInputRecordTokens(inputRecord, taskQuery);
			
			if (objectType.equals("local_label_update__c")) {
				numOfTaskCreated = createLocalLabelUpdateTask(inputRecord, taskQuery, taskObjectType.get("local_label_update__c"));
			} else if (objectType.equals("aggregate_report_dissemination__c")) {
				numOfTaskCreated = createAggregateReportTask(inputRecord, taskQuery, taskObjectType.get("aggregate_report_dissemination__c"));
			} else if (objectType.equals("rems_update__c")) {
				numOfTaskCreated = createRiskManagementTask(inputRecord, taskQuery, taskObjectType.get("rems_update__c"));
			} else {
				if (isTaskDistinct(taskQuery)) {
					taskQuery = StringUtils.replaceAll(taskQuery, "\\$\\{DISTINCT\\}", "");
					numOfTaskCreated = createTask(inputRecord, taskQuery, true, taskObjectType.get(objectType), sdkSetting);
				} else {
					numOfTaskCreated = createTask(inputRecord, taskQuery, false, taskObjectType.get(objectType), sdkSetting);
				}
			}

			// Set the task related details
			String currentUserId = RequestContext.get().getCurrentUserId();
			inputRecord.getNew().setValue("tasks_generated_by__c", currentUserId);
			inputRecord.getNew().setValue("tasks_generated_on__c", ZonedDateTime.now());
			inputRecord.getNew().setValue("of_tasks_created__c", new BigDecimal(numOfTaskCreated));
		} else {
			// Throw error if no task is generated
			inputRecord.setError("OPERATION_NOT_ALLOWED",
					"No Task Query was detected.  Please check the Task Query field and make sure it is populated.");
		}

	}
	


	
	/**
	 * Generates multi-agreement task based on Local Label Update. 
	 * This is a hard code specific scenario to join 2 queries together 
	 * because OR clause are not supported for joined queries.
	 * 
	 * @param queryService the query service
	 * @param inputRecord the input record
	 * @param query the query, separated by |
	 * @return the number of task created.
	 * 
	 */
	@SuppressWarnings("unchecked")
	private static int createLocalLabelUpdateTask(RecordChange inputRecord, String query, String objectType) {
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		String objectId = inputRecord.getNew().getValue("id", ValueType.STRING);
		//String[] queryList = query.split("\\|");
		String[] queryList = StringUtils.split(query,"\\|");
		// Use a set to maintain a list of unique results
		Set<String> recordSet = VaultCollections.newSet();
		List<Record> recordList = VaultCollections.newList();
		int taskGeneratedCount = 0;
		
		for (String queryStatement:queryList) {
			QueryResponse queryResponse = QueryServiceUtil.query(queryStatement);
			Log.debug("Executing: " + queryStatement);
			Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
			while (iterator.hasNext()) {
				QueryResult queryResult = iterator.next();
				QueryResponse subQueryResponse = queryResult.getSubqueryResponse("bdl_agreement_contacts__cr");
				Iterator<QueryResult> subIterator = subQueryResponse.streamResults().iterator();
				while (subIterator.hasNext()) {
					QueryResult subQueryResult = subIterator.next();
					String agreementContact = subQueryResult.getValue("id", ValueType.STRING);
					String contact = subQueryResult.getValue("contact__c", ValueType.STRING);
					String partner = subQueryResult.getValue("partner__c", ValueType.STRING);
					String agreement = subQueryResult.getValue("agreement__c", ValueType.STRING);
					String executedDeal = subQueryResult.getValue("executed_deal__c", ValueType.STRING);
					if (partner !=null && agreement !=null && executedDeal != null) {
						if (!partner.isEmpty() && !agreement.isEmpty() && !executedDeal.isEmpty()) {
							// Set is contact+partner+agreement+executed deal as the unique key
							// Any one of those values cannot be null
							if (contact == null) {
								contact = "";
							}
							String key = contact + partner + agreement + executedDeal;
							if (!recordSet.contains(key)) {
								Record record = recordService.newRecord(TASK_OBJECT_NAME);
								if (contact != null && !contact.isEmpty()) {
									record.setValue("contact__c", contact);
								}
								record.setValue("bdl_agreement_contact__c", agreementContact);
								record.setValue("object_type__v", objectType);
								record.setValue("partner__c", partner);
								record.setValue("bdl_agreement__c", agreement);
								record.setValue("bdl_executed_deal__c", executedDeal);
								record.setValue("multi_agreement_activity__c", objectId);
								recordList.add(record);
								recordSet.add(key);
							}
						}
					}
				}
			}
			
		}
		
		
		taskGeneratedCount = recordList.size();
		
		if (taskGeneratedCount == 0) {
			// Throw an error if the query returned 0 results to prevent the state
			// from changing.
			inputRecord.setError("OPERATION_NOT_ALLOWED",
					"No task were generated based on the setup criteria.  Please check your data setup.");
		} else if (taskGeneratedCount == MAX_TASK_GENERATION_LIMIT) {
			inputRecord.setError("OPERATION_NOT_ALLOWED", "The number of task to be generated exceeded " + MAX_TASK_GENERATION_LIMIT 
					+ ".  The first 5000 task has been generated.  Please manually generate the rest of the task. "
					+ "If you feel this is an error, please contact your IT Administrator.");
		} else {
			RecordServiceUtil.updateRecordList(recordList, TASK_OBJECT_NAME);
		}
		
		return taskGeneratedCount;
	}
	
	@SuppressWarnings("unchecked")
	private int createRiskManagementTask(RecordChange inputRecord, String query, String objectType) {

		Map<String, String> queryMap = VaultCollections.newMap();
		queryMap.put("bdl_agreement_contacts__cr.executed_deal__c", "bdl_executed_deal__c");
		queryMap.put("bdl_agreement_contacts__cr.agreement__c", "bdl_agreement__c");
		queryMap.put("bdl_agreement_contacts__cr.partner__c", "partner__c");
		queryMap.put("bdl_agreement_contacts__cr.contact__c", "contact__c");
		queryMap.put("bdl_agreement_contacts__cr.id", "bdl_agreement_contact__c");
		queryMap.put("bdl_risk_management__cr.id", "risk_management_term__c");
		// Task Mapping - END

		RecordService recordService = ServiceLocator.locate(RecordService.class);
		String objectId = inputRecord.getNew().getValue("id", ValueType.STRING);
		// Use a set to maintain a list of unique results
		Set<String> recordSet = VaultCollections.newSet();
		List<Record> recordList = VaultCollections.newList();
		int taskGeneratedCount = 0;
		
		QueryResponse queryResponse = QueryServiceUtil.query(query);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
		while (iterator.hasNext()) {
			QueryResult queryResult = iterator.next();
			QueryResponse subQueryResponseAggregate = queryResult.getSubqueryResponse("bdl_risk_management__cr");
			Iterator<QueryResult> subIteratorAggregate = subQueryResponseAggregate.streamResults().iterator();
			while (subIteratorAggregate.hasNext()) {
				QueryResult subQueryResultAggregate = subIteratorAggregate.next();
				String riskManagementId = subQueryResultAggregate.getValue("id", ValueType.STRING);
				
				QueryResponse subQueryResponse = queryResult.getSubqueryResponse("bdl_agreement_contacts__cr");
				Iterator<QueryResult> subIterator = subQueryResponse.streamResults().iterator();
				while (subIterator.hasNext()) {
					QueryResult subQueryResult = subIterator.next();
					String agreementContact = subQueryResult.getValue("id", ValueType.STRING);
					String contact = subQueryResult.getValue("contact__c", ValueType.STRING);
					String partner = subQueryResult.getValue("partner__c", ValueType.STRING);
					String agreement = subQueryResult.getValue("agreement__c", ValueType.STRING);
					String executedDeal = subQueryResult.getValue("executed_deal__c", ValueType.STRING);
					String riskManagement = riskManagementId;
					if (partner !=null && agreement !=null && executedDeal != null) {
						if (!partner.isEmpty() && !agreement.isEmpty() && !executedDeal.isEmpty() && !riskManagement.isEmpty()) {
							// Set is contact+partner+agreement+executed deal as the unique key
							// Any one of those values cannot be null
							if (contact == null) {
								contact = "";
							}
							String key = contact + partner + agreement + executedDeal + riskManagement;
							if (!recordSet.contains(key)) {
								Record record = recordService.newRecord(TASK_OBJECT_NAME);
								if (contact != null && !contact.isEmpty()) {
									record.setValue("contact__c", contact);
								}
								record.setValue("object_type__v", objectType);
								record.setValue("bdl_agreement_contact__c", agreementContact);
								record.setValue("partner__c", partner);
								record.setValue("bdl_agreement__c", agreement);
								record.setValue("bdl_executed_deal__c", executedDeal);
								record.setValue("multi_agreement_activity__c", objectId);
								record.setValue("risk_management_term__c",  riskManagement);
								recordList.add(record);
								recordSet.add(key);
							}
						}
					}
				}
			}
			
		}
		
		taskGeneratedCount = recordList.size();
		
		if (taskGeneratedCount == 0) {
			// Throw an error if the query returned 0 results to prevent the state
			// from changing.
			inputRecord.setError("OPERATION_NOT_ALLOWED",
					"No task were generated based on the setup criteria.  Please check your data setup.");
		} else if (taskGeneratedCount == MAX_TASK_GENERATION_LIMIT) {
			inputRecord.setError("OPERATION_NOT_ALLOWED", "The number of task to be generated exceeded " + MAX_TASK_GENERATION_LIMIT 
					+ ".  The first 5000 task has been generated.  Please manually generate the rest of the task. "
					+ "If you feel this is an error, please contact your IT Administrator.");
		} else {
			RecordServiceUtil.updateRecordList(recordList, TASK_OBJECT_NAME);
		}
		
		return taskGeneratedCount;
	}
	
	@SuppressWarnings("unchecked")
	private int createAggregateReportTask(RecordChange inputRecord, String query, String objectType) {

		Map<String, String> queryMap = VaultCollections.newMap();
		queryMap.put("bdl_agreement_contacts__cr.executed_deal__c", "bdl_executed_deal__c");
		queryMap.put("bdl_agreement_contacts__cr.agreement__c", "bdl_agreement__c");
		queryMap.put("bdl_agreement_contacts__cr.partner__c", "partner__c");
		queryMap.put("bdl_agreement_contacts__cr.contact__c", "contact__c");
		queryMap.put("bdl_agreement_contacts__cr.id", "bdl_agreement_contact__c");
		queryMap.put("bdl_aggregate_reports__cr.id", "aggregate_report_term__c");
		// Task Mapping - END

		RecordService recordService = ServiceLocator.locate(RecordService.class);
		String objectId = inputRecord.getNew().getValue("id", ValueType.STRING);
		// Use a set to maintain a list of unique results
		Set<String> recordSet = VaultCollections.newSet();
		List<Record> recordList = VaultCollections.newList();
		int taskGeneratedCount = 0;
		
		QueryResponse queryResponse = QueryServiceUtil.query(query);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
		while (iterator.hasNext()) {
			QueryResult queryResult = iterator.next();
			QueryResponse subQueryResponseAggregate = queryResult.getSubqueryResponse("bdl_aggregate_reports__cr");
			Iterator<QueryResult> subIteratorAggregate = subQueryResponseAggregate.streamResults().iterator();
			while (subIteratorAggregate.hasNext()) {
				QueryResult subQueryResultAggregate = subIteratorAggregate.next();
				String aggregateReportId = subQueryResultAggregate.getValue("id", ValueType.STRING);
				
				QueryResponse subQueryResponse = queryResult.getSubqueryResponse("bdl_agreement_contacts__cr");
				Iterator<QueryResult> subIterator = subQueryResponse.streamResults().iterator();
				while (subIterator.hasNext()) {
					QueryResult subQueryResult = subIterator.next();
					String agreementContact = subQueryResult.getValue("id", ValueType.STRING);
					String contact = subQueryResult.getValue("contact__c", ValueType.STRING);
					String partner = subQueryResult.getValue("partner__c", ValueType.STRING);
					String agreement = subQueryResult.getValue("agreement__c", ValueType.STRING);
					String executedDeal = subQueryResult.getValue("executed_deal__c", ValueType.STRING);
					String aggregateReport = aggregateReportId;
					if (partner !=null && agreement !=null && executedDeal != null) {
						if (!partner.isEmpty() && !agreement.isEmpty() && !executedDeal.isEmpty() && !aggregateReport.isEmpty()) {
							// Set is contact+partner+agreement+executed deal as the unique key
							// Any one of those values cannot be null
							if (contact == null) {
								contact = "";
							}
							String key = contact + partner + agreement + executedDeal + aggregateReport;
							if (!recordSet.contains(key)) {
								Record record = recordService.newRecord(TASK_OBJECT_NAME);
								if (contact != null && !contact.isEmpty()) {
									record.setValue("contact__c", contact);
								}
								record.setValue("object_type__v", objectType);
								record.setValue("bdl_agreement_contact__c", agreementContact);
								record.setValue("partner__c", partner);
								record.setValue("bdl_agreement__c", agreement);
								record.setValue("bdl_executed_deal__c", executedDeal);
								record.setValue("multi_agreement_activity__c", objectId);
								record.setValue("aggregate_report_term__c", aggregateReport);
								recordList.add(record);
								recordSet.add(key);
							}
						}
					}
				}
			}
			
		}
		
		taskGeneratedCount = recordList.size();
		
		if (taskGeneratedCount == 0) {
			// Throw an error if the query returned 0 results to prevent the state
			// from changing.
			inputRecord.setError("OPERATION_NOT_ALLOWED",
					"No task were generated based on the setup criteria.  Please check your data setup.");
		} else if (taskGeneratedCount == MAX_TASK_GENERATION_LIMIT) {
			inputRecord.setError("OPERATION_NOT_ALLOWED", "The number of task to be generated exceeded " + MAX_TASK_GENERATION_LIMIT 
					+ ".  The first 5000 task has been generated.  Please manually generate the rest of the task. "
					+ "If you feel this is an error, please contact your IT Administrator.");
		} else {
			RecordServiceUtil.updateRecordList(recordList, TASK_OBJECT_NAME);
		}
		
		return taskGeneratedCount;
	}
	
	/**
	 * Determines if a task query have the distinct keyword
	 * 
	 * @param taskQuery the task query
	 * @return true if there is a distinct keyword, false otherwise
	 */
	private static boolean isTaskDistinct(String taskQuery) {
		Set<String> tokens = TriggerUtil.retrieveTokensFromText(taskQuery);
		for (String token : tokens) {
			if (token.equals("DISTINCT")) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Create the task from the query
	 * 
	 * @param queryService
	 *            the query service
	 * @param inputRecord
	 *            the trigger record
	 * @param tokenQuery
	 *            the query to be executed
	 * @param columnNames
	 *            the names of the query column
	 * @return the number of task created
	 */
	@SuppressWarnings("unchecked")
	private int createTask(RecordChange inputRecord, String query, boolean isDistinct, String objectType, SDKSetting sdkSetting) {
		
		// Validate the joined limit
		Map<String, Map<String,String>> activityToTaskMappings = sdkSetting.getMappings();
		if (activityToTaskMappings.size() > 2) {
			inputRecord.setError("OPERATION_NOT_ALLOWED", "Task Query contains more than 2 select clause. The maximum limit is 2.");
		}
		
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		List<Record> recordList = VaultCollections.newList();
		String objectId = inputRecord.getNew().getValue("id", ValueType.STRING);
		Log.debug("Processing record: " + objectId);
		QueryResponse queryResponse = QueryServiceUtil.query(query);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
		int taskGeneratedCount = 0;
		
		
		while (iterator.hasNext()) {
			QueryResult queryResult = iterator.next();
			if (activityToTaskMappings.size() == 1) {
				// No joined relationship in the query
				if (activityToTaskMappings.containsKey("bdl_multi_agreement_activity__c")) {
					Map<String,String> mainMapping = activityToTaskMappings.get("bdl_multi_agreement_activity__c");
					Record record = recordService.newRecord(TASK_OBJECT_NAME);
					record.setValue("object_type__v", objectType);
					record.setValue("multi_agreement_activity__c", objectId);
					for (String fieldName:mainMapping.keySet()) {
						record.setValue(mainMapping.get(fieldName), queryResult.getValue(fieldName, ValueType.STRING));
					}
					recordList.add(record);	
				} else {
					Log.debug("Processing Subquery results");
					// One joined relationship in the query
					List<String> distinctRecordSet = VaultCollections.newList();
					for (String relationship:activityToTaskMappings.keySet()) {
						if (!relationship.equals("bdl_multi_agreement_activity__c")) {
							Map<String,String> subMapping = activityToTaskMappings.get(relationship);
							QueryResponse subQueryResponse = queryResult.getSubqueryResponse(relationship);
							Iterator<QueryResult> subIterator = subQueryResponse.streamResults().iterator();
							
							while (subIterator.hasNext()) {
								Record record = recordService.newRecord(TASK_OBJECT_NAME);
								record.setValue("object_type__v", objectType);
								record.setValue("multi_agreement_activity__c", objectId);
								Log.debug("Object Type:" +  objectType);
								QueryResult subQueryResult = subIterator.next();
								if (isDistinct) {
									List<String> listofFields = VaultCollections.newList();
									for (String fieldName:subMapping.keySet()) {
										String key = subMapping.get(fieldName) + subQueryResult.getValue(fieldName, ValueType.STRING);
										listofFields.add(key);
										record.setValue(subMapping.get(fieldName), subQueryResult.getValue(fieldName, ValueType.STRING));
									}
									java.util.Collections.sort(listofFields);
									String key = String.join("-", listofFields);
									if (!distinctRecordSet.contains(key)) {
										distinctRecordSet.add(key);
										Log.debug("Adding record:" + key);
										recordList.add(record);	
									}
									
								} else {
									for (String fieldName:subMapping.keySet()) {
										record.setValue(subMapping.get(fieldName), subQueryResult.getValue(fieldName, ValueType.STRING));
										Log.debug("Setting: " + subMapping.get(fieldName) + "," + subQueryResult.getValue(fieldName, ValueType.STRING));
									}
									recordList.add(record);	
								}
								
							}
						}
					}
				}
			} 
		
		}
		
		taskGeneratedCount = recordList.size();
		
		if (taskGeneratedCount == 0) {
			// Throw an error if the query returned 0 results to prevent the state
			// from changing.
			inputRecord.setError("OPERATION_NOT_ALLOWED",
					"No task were generated based on the setup criteria.  Please check your data setup.");
		} else if (taskGeneratedCount == MAX_TASK_GENERATION_LIMIT) {
			inputRecord.setError("OPERATION_NOT_ALLOWED", "The number of task to be generated exceeded " + MAX_TASK_GENERATION_LIMIT 
					+ ".  The first 5000 task has been generated.  Please manually generate the rest of the task. "
					+ "If you feel this is an error, please contact your IT Administrator.");
		} else {
			RecordServiceUtil.updateRecordList(recordList, TASK_OBJECT_NAME);
		}


		return taskGeneratedCount;
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
	@SuppressWarnings("unchecked")
	private String resolveInputRecordTokens(RecordChange inputRecord, String text) {
		final String methodName = BDLGenerateMultiAgreementTask.class.toString() + ".resolveInputRecordTokens()";
		Log.entry(methodName);
		Set<String> tokens = TriggerUtil.retrieveTokensFromText(text);
		for (String token : tokens) {
			// 2.8 Release.  Resolve token with . for related objects.
			if (token.contains(".")) {
				boolean isRequired = false;
				String[] clauses = StringUtils.split(token, "\\.");
				String objectName = clauses[0];
				String fieldName = clauses[1];
				if (clauses.length > 2) {
					if (clauses[2] != null && !clauses[2].isEmpty() && clauses[2].equalsIgnoreCase("required")) {
						isRequired = true;
					}
				}
				String objectId = inputRecord.getNew().getValue("id", ValueType.STRING);
				String vql="SELECT " + fieldName + " FROM " + objectName + " where bdl_multi_agreement_activity__c='" + objectId + "'";
				QueryResponse queryResponse = QueryServiceUtil.query(vql);
				List<String> fieldValues = VaultCollections.newList();
				Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
				while (iterator.hasNext()) {
					QueryResult row = iterator.next();
					String fieldValue = row.getValue(fieldName, ValueType.STRING);
					fieldValues.add(fieldValue);
				}
				
				if (isRequired & clauses.length == 4 && fieldValues.size() == 0) {
					if (clauses[3] != null && !clauses[3].isEmpty()) {
						fieldName = clauses[3];
					}
					throw new RollbackException("OPERATION_NOT_ALLOWED",
							"At least one " + fieldName + " is required.  Please add a " + fieldName + ".");
				}
				
				text = StringUtils.replaceAll(text, "\\$\\{" + token + "\\}", "('" + String.join("','", fieldValues) + "')");				
			}
		}
		Log.exit(methodName);
		return text;
	}
	
	
	
	
}