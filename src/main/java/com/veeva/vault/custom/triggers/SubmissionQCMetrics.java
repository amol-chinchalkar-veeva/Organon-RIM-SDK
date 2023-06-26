package com.veeva.vault.custom.triggers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * Object: submission__v
 * Trigger API Name: submission_qc_metrics__c
 * Author: Richard Pater
 * Date: 14 December 2017
 *
 * Automatically create QC Metrics (qc_metrics__c) related object when Submission 
 * enters state 'In Archive Team Review'.  Populate QC Metrics object with data 
 * as Submission progresses through workflow until reaching Final state or returning 
 * to Draft state.
 *
 * ON AFTER UPDATE:
 * 1. Check lifecycle state of Submission
 * 2. If new state = in_archive_team_review_state__c, in_qc_state__c, active_state__c (Draft), or final_state__c continue else exit
 * 3. Query qc_metrics__c, filtering based on Submission and where Result is blank
 * 4a. If state = Draft and there is a query result
 * 		5a. Update the Result to Draft, the Date Completed to now, populate Reason for Rejection, Rejection Comments, calculate and populate 
 * 			Days Final to Completed and On Time and exit
 * 		6a. Calculate Days Final to Completed by counting days between Date Received and Date Completed, skipping weekends and incrementing Date Received if after 4pm EST
 * 4b. If state = Final and there is a query result
 * 		5b. Update the Result to Final, the Date Completed to now, Review Assignee (Person who Finalized), calculate and populate Days Final to Completed and On Time 
 * 			and exit
 * 		6b. Calculate Days Final to Completed by counting days between Date Received and Date Completed, skipping weekends and incrementing Date Received if after 4pm EST
 * 4c. If state = In QC and there is a query result
 * 		5c. Populate Date Assigned to now and exit
 * 4d. If state = In Archive Team Review and there is NOT a query result
 * 		5d. Create new QC Metrics object for this Submission, populate Application, populate Request Type, populate On Time (In Progress) and populate Date Received to now
 * 		
 */

@RecordTriggerInfo(object = "submission__v", 
	name="submission_qc_metrics__c", 
	events = { RecordEvent.AFTER_UPDATE }, 
	order = TriggerOrder.NUMBER_1)

public class SubmissionQCMetrics implements RecordTrigger {

	//Constants for this trigger
	//Objects
	private static final String SUBMISSION_OBJECT_NAME = "submission__v";
	private static final String QC_METRICS_OBJECT_NAME = "qc_metrics__c";
	
	//Submission LC States
	private static final String DRAFT_STATE = "active_state__c";
	private static final String IN_ARCHIVE_TEAM_REVIEW_STATE = "in_archive_team_review_state__c";
	private static final String IN_QC_STATE = "in_qc_state__c";
	private static final String FINAL_STATE = "final_state__c";
	
	//Submission Fields
	private static final String SUB_ID = "id"; //String
	private static final String SUB_APPLICATION = "application__v"; //String
	private static final String SUB_LIFECYCLE_STATE = "state__v"; //String
	private static final String SUB_PERSON_WHO_FINALIZED = "person_who_finalized1__c"; //Picklist
	private static final String SUB_REASON_FOR_REJECTION = "reason_for_rejection__c"; //Picklist
	private static final String SUB_REJECTION_COMMENTS = "rejection_comments__c"; //String
	
	
	//QC Metrics Fields
	private static final String QCM_ID = "id"; //String
	private static final String QCM_DATE_ASSIGNED = "date_assigned__c"; //Date
	private static final String QCM_DATE_COMPLETED = "date_completed__c"; //Date
	private static final String QCM_DATE_RECEIVED = "date_received__c"; //Date
	private static final String QCM_DAYS_FINAL_TO_COMPLETED = "days_final_to_completed__c"; //Number
	private static final String QCM_ON_TIME = "on_time__c"; //Picklist
	private static final String QCM_REASON_FOR_REJECTION = "reason_for_rejection__c"; //Picklist
	private static final String QCM_REJECTION_COMMENTS = "rejection_comments__c"; //String
	private static final String QCM_REQUEST_TYPE = "request_type__c"; //Picklist (Always Submission)
	private static final String QCM_REQUESTOR = "requestor__c"; //String
	private static final String QCM_RESULT = "result__c"; //Picklist
	private static final String QCM_REVIEW_ASSIGNEE = "review_assignee__c"; //Picklist
	private static final String QCM_APPLICATION = "application__c"; //Object
	private static final String QCM_SUBMISSION = "submission__c"; //Object
	private static final String QCM_CURRENT = "current__c"; //Picklist
	private static final String QCM_CYCLE_COUNT = "cycle_count__c"; //Number
	
	//QCM Picklists
	//Result
	private static final String QCM_RESULT_DRAFT = "draft__c";
	private static final String QCM_RESULT_FINAL = "final__c";
	//Request Type
	private static final String QCM_REQUEST_TYPE_SUBMISSIONS = "submissions__c";
	private static final String QCM_REQUEST_TYPE_CORRESPONDENCE = "correspondence__c";
	//On Time
	private static final String QCM_ON_TIME_YES = "yes__c";
	private static final String QCM_ON_TIME_NO = "no__c";
	private static final String QCM_ON_TIME_IN_PROGRESS = "in_progress__c";
	//Current
	private static final String QCM_CURRENT_CURRENT = "current__c";
	private static final String QCM_CURRENT_SUPERSEDED = "superseded__c";
	
	//Vault SDK constants
	private static final int BATCH_LIMIT = 500;
	
	/*
	 * Trigger overide for the execute method
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.veeva.vault.sdk.api.data.RecordTrigger#execute(com.veeva.vault.sdk.
	 * api.data.RecordTriggerContext)
	 */
	public void execute(RecordTriggerContext recordTriggerContext) {
		final String SINGLE_QUOTE = String.valueOf((char) 39);
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
		LocalDate today = now.toLocalDate();

		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			try {
				if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_UPDATE) {
					String newSubState = inputRecord.getNew().getValue(SUB_LIFECYCLE_STATE, ValueType.STRING);
					String oldSubState = inputRecord.getOld().getValue(SUB_LIFECYCLE_STATE, ValueType.STRING);
					String submissionID = inputRecord.getNew().getValue(SUB_ID, ValueType.STRING);
					String applicationID = inputRecord.getNew().getValue(SUB_APPLICATION, ValueType.STRING);
					//Only run code if this is a state change for the Submission
					if (!newSubState.equals(oldSubState)) {
						QueryService queryService = ServiceLocator.locate(QueryService.class);
						RecordService recordService = ServiceLocator.locate(RecordService.class);
						//Query QC Metrics
						QueryResponse queryResponse = null;
	                    Iterator<QueryResult> iterator = null;
	                    
	                    String query = "select " + QCM_ID +"," + QCM_DATE_RECEIVED + " from " + QC_METRICS_OBJECT_NAME + " where " + QCM_SUBMISSION + " = "+ SINGLE_QUOTE + submissionID + SINGLE_QUOTE + " and " + QCM_RESULT + " = null";
	                    queryResponse = queryService.query(query);
	                    iterator = queryResponse.streamResults().iterator();

	                    Record qcmRecord = null;
	                    List<Record> qcmRecordList = VaultCollections.newList();
	                    while (iterator.hasNext()) {
	                        QueryResult queryResult = iterator.next();
	                        qcmRecord = recordService.newRecord(QC_METRICS_OBJECT_NAME);
	                        qcmRecord.setValue(QCM_ID, queryResult.getValue(QCM_ID, ValueType.STRING));
	                        qcmRecord.setValue(QCM_DATE_RECEIVED, queryResult.getValue(QCM_DATE_RECEIVED, ValueType.DATE));
	                        qcmRecordList.add(qcmRecord);
	                    }
						
						if (newSubState.equals(DRAFT_STATE) && qcmRecord != null) {
							/*
							 * 4a. If state = Draft and there is a query result
							 * 		5a. Update the Result to Draft, the Date Completed to now, populate Reason for Rejection, Rejection Comments, calculate and populate 
							 * 			Days Final to Completed and On Time and exit
							 * 		6a. Calculate Days Final to Completed by counting days between Date Received and Date Completed, skipping weekends and incrementing Date Received if after 4pm EST
							 */
							List<String> resultList = VaultCollections.newList();
							resultList.add(QCM_RESULT_DRAFT);
							qcmRecord.setValue(QCM_RESULT,resultList);
							qcmRecord.setValue(QCM_DATE_COMPLETED,today);
							List<String> subReasonForRejection = inputRecord.getNew().getValue(SUB_REASON_FOR_REJECTION, ValueType.PICKLIST_VALUES);
							qcmRecord.setValue(QCM_REASON_FOR_REJECTION,subReasonForRejection);
							String subRejectionComments = "";
							subRejectionComments = inputRecord.getNew().getValue(SUB_REJECTION_COMMENTS, ValueType.STRING);
							qcmRecord.setValue(QCM_REJECTION_COMMENTS,subRejectionComments);
							
							BigDecimal daysFinalToCompleted = null;
							daysFinalToCompleted = calculateTurnaroundTime(qcmRecord.getValue(QCM_DATE_RECEIVED, ValueType.DATE), today);
							qcmRecord.setValue(QCM_DAYS_FINAL_TO_COMPLETED,daysFinalToCompleted);
							qcmRecord.setValue(QCM_ON_TIME,isOnTime(daysFinalToCompleted));
							
						} else if (newSubState.equals(FINAL_STATE) && qcmRecord != null) {
							/* 4b. If state = Final and there is a query result
							 * 		5b. Update the Result to Final, the Date Completed to now, Review Assignee (Person who Finalized), calculate and populate Days Final to Completed and On Time 
							 * 			and exit
							 * 		6b. Calculate Days Final to Completed by counting days between Date Received and Date Completed, skipping weekends and incrementing Date Received if after 4pm EST
							 */
							List<String> resultList = VaultCollections.newList();
							resultList.add(QCM_RESULT_FINAL);
							qcmRecord.setValue(QCM_RESULT,resultList);
							qcmRecord.setValue(QCM_DATE_COMPLETED,today);
							List<String> reviewAssignee = inputRecord.getNew().getValue(SUB_PERSON_WHO_FINALIZED, ValueType.PICKLIST_VALUES);
							qcmRecord.setValue(QCM_REVIEW_ASSIGNEE,reviewAssignee);
							
							BigDecimal daysFinalToCompleted = null;
							daysFinalToCompleted = calculateTurnaroundTime(qcmRecord.getValue(QCM_DATE_RECEIVED, ValueType.DATE), today);
							qcmRecord.setValue(QCM_DAYS_FINAL_TO_COMPLETED,daysFinalToCompleted);
							qcmRecord.setValue(QCM_ON_TIME,isOnTime(daysFinalToCompleted));
							
						} else if (newSubState.equals(IN_QC_STATE) && qcmRecord != null) {
							/*
							 * 4c. If state = In QC and there is a query result
							 * 		5c. Populate Date Assigned to now and exit
							 */
							qcmRecord.setValue(QCM_DATE_ASSIGNED,today);
						} else if (newSubState.equals(IN_ARCHIVE_TEAM_REVIEW_STATE) && qcmRecord == null) {
							/*
							 * 4d. If state = In Archive Team Review and there is NOT a query result
							 * 		5d. Create new QC Metrics object for this Submission, populate Application, populate Request Type, populate On Time (In Progress) and populate Date Received to now
							 */
							int cycleCount = 0;
							//Query for an existing QC Metrics Object where current__c = current__c
							query = "select " + QCM_ID +"," + QCM_CYCLE_COUNT + " from " + QC_METRICS_OBJECT_NAME + " where " + QCM_SUBMISSION + " = "+ SINGLE_QUOTE + submissionID + SINGLE_QUOTE + " and " + QCM_RESULT + " != null and " + QCM_CURRENT + " = " + SINGLE_QUOTE + QCM_CURRENT_CURRENT + SINGLE_QUOTE;
		                    queryResponse = queryService.query(query);
		                    iterator = queryResponse.streamResults().iterator();

		                    qcmRecord = null;
		                    while (iterator.hasNext()) {
		                        QueryResult queryResult = iterator.next();
		                        qcmRecord = recordService.newRecord(QC_METRICS_OBJECT_NAME);
		                        qcmRecord.setValue(QCM_ID, queryResult.getValue(QCM_ID, ValueType.STRING));
		                        //Set Current field to Superseded
		                        List<String> supersededList = VaultCollections.newList();
		                        supersededList.add(QCM_CURRENT_SUPERSEDED);
		                        qcmRecord.setValue(QCM_CURRENT, supersededList);
		                        //Read cycle count
		                        BigDecimal cycleCountBD = queryResult.getValue(QCM_CYCLE_COUNT, ValueType.NUMBER);
		                        cycleCount = cycleCountBD.intValue();
		                        qcmRecordList.add(qcmRecord);
		                    }
							
							cycleCount++;
							Record r = recordService.newRecord(QC_METRICS_OBJECT_NAME);
							r.setValue(QCM_APPLICATION, applicationID);
							r.setValue(QCM_SUBMISSION, submissionID);
							List<String> requestTypeList = VaultCollections.newList();
							requestTypeList.add(QCM_REQUEST_TYPE_SUBMISSIONS);
							r.setValue(QCM_REQUEST_TYPE, requestTypeList);
							List<String> onTimeList = VaultCollections.newList();
							onTimeList.add(QCM_ON_TIME_IN_PROGRESS);
							r.setValue(QCM_ON_TIME, onTimeList);
							List<String> currentList = VaultCollections.newList();
							currentList.add(QCM_CURRENT_CURRENT);
							r.setValue(QCM_CURRENT, currentList);
							r.setValue(QCM_CYCLE_COUNT, new BigDecimal(cycleCount));
							ZonedDateTime dateReceived = now;
							if (dateReceived.getHour() >= 16) {
								//Add day
								dateReceived = dateReceived.plusDays(1);
							}
							while (dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
								//Set dateReceived forward 1 day
								dateReceived = dateReceived.plusDays(1);
							}
							r.setValue(QCM_DATE_RECEIVED, dateReceived.toLocalDate());
							qcmRecordList.add(r);
						}
						recordService.batchSaveRecords(qcmRecordList)
			        	.onErrors(batchOperationErrors -> {
			        			batchOperationErrors.stream().findFirst().ifPresent(error -> {
			        				String errMsg = error.getError().getMessage();
			        				throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
			        			});
			        	})
			        	.execute();
					}
				}
			} catch(VaultRuntimeException e) {
	        	RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED",
	     	            e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
	     	    throw rollbackException;

	        }
		}
	}

	private List<String> isOnTime(BigDecimal daysFinalToCompleted) {
		List<String> onTimeList = VaultCollections.newList();
		if (daysFinalToCompleted != null && daysFinalToCompleted.intValue() <= 2) {
			onTimeList.add(QCM_ON_TIME_YES);
		} else {
			onTimeList.add(QCM_ON_TIME_NO);
		}
		return onTimeList;
	}

	private BigDecimal calculateTurnaroundTime(LocalDate dateReceived, LocalDate dateCompleted) {
		BigDecimal dayCount = new BigDecimal(0);
		//If dateReceived is after 4pm, start counting from next day
		/*
		if (dateReceived.getHour() >= 16) {
			//Add day, reset to 9am
			dateReceived = dateReceived.plusDays(1);
			dateReceived = dateReceived.withHour(9).withMinute(0).withSecond(0);
		}
		*/
		while (dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
			//Set dateReceived forward 1 day, reset time to 9am
			dateReceived = dateReceived.plusDays(1);
			//dateReceived = dateReceived.withHour(9).withMinute(0).withSecond(0);
		}
		//We now have adjusted dateReceived to be used in calculation
		//int businessHourCount = 0;
		int businessDayCount = 0;
		while (dateReceived.isBefore(dateCompleted)) {
			if (dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
				//Set dateReceived forward 1 day
				dateReceived = dateReceived.plusDays(1);
			} else {
				//dateReceived = dateReceived.plusHours(1);
				//businessHourCount++;
				dateReceived = dateReceived.plusDays(1);
				businessDayCount++;
			}
		}
		//dayCount = new BigDecimal(businessHourCount / 24);
		dayCount = new BigDecimal(businessDayCount);
		return dayCount;
	}
}