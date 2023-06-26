package com.veeva.vault.custom.triggers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;

/**
 * Object: qc_metrics__c
 * Trigger API Name: qc_metrics_validations__c
 * Author: Richard Pater
 * Date: 19 December 2017
 *
 * Calculate and update DaysFinalToCompleted and OnTime for Correspondence type 
 * QC Metrics (qc_metrics__c).
 *
 * ON BEFORE INSERT:
 * 1. If Date Received and Date Completed are both populated, calculate and populate Days Final To Completed and On Time
 * 
 * ON BEFORE UPDATE:
 * 1. If there is a new value for either Date Received or Date Completed and both are populated, calculate and populate Days Final To Completed and On Time
 */

@RecordTriggerInfo(object = "qc_metrics__c", 
	name="qc_metrics_validations__c", 
	events = { RecordEvent.BEFORE_INSERT,RecordEvent.BEFORE_UPDATE }, 
	order = TriggerOrder.NUMBER_1)

public class QCMetricsValidations implements RecordTrigger {
	private static final String QC_METRICS_OBJECT_NAME = "qc_metrics__c";
	
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
				String requestType = "";
				List<String> requestTypeList = inputRecord.getNew().getValue(QCM_REQUEST_TYPE, ValueType.PICKLIST_VALUES);
				if (requestTypeList != null && !requestTypeList.isEmpty()) {
					requestType = requestTypeList.get(0);
				}
				if (requestType.equals(QCM_REQUEST_TYPE_CORRESPONDENCE)) {
					if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) {
						List<String> onTimeList = VaultCollections.newList();
						onTimeList.add(QCM_ON_TIME_IN_PROGRESS);
						inputRecord.getNew().setValue(QCM_ON_TIME, onTimeList);
						LocalDate newDateReceived = inputRecord.getNew().getValue(QCM_DATE_RECEIVED, ValueType.DATE);
						if (newDateReceived != null) {
							if (now.getHour() >= 16) {
								newDateReceived = newDateReceived.plusDays(1);
							}
							while (newDateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || newDateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
								//Set dateReceived forward 1 day
								newDateReceived = newDateReceived.plusDays(1);
							}
							inputRecord.getNew().setValue(QCM_DATE_RECEIVED, newDateReceived);
							LocalDate newDateCompleted = inputRecord.getNew().getValue(QCM_DATE_COMPLETED, ValueType.DATE);
							if (newDateReceived != null && newDateCompleted != null) {
								BigDecimal daysFinalToCompleted = null;
								daysFinalToCompleted = calculateTurnaroundTime(newDateReceived, newDateCompleted);
								inputRecord.getNew().setValue(QCM_DAYS_FINAL_TO_COMPLETED,daysFinalToCompleted);
								inputRecord.getNew().setValue(QCM_ON_TIME,isOnTime(daysFinalToCompleted));
							}
						}
					} else if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
						LocalDate newDateReceived = inputRecord.getNew().getValue(QCM_DATE_RECEIVED, ValueType.DATE);
						LocalDate newDateCompleted = inputRecord.getNew().getValue(QCM_DATE_COMPLETED, ValueType.DATE);
						LocalDate oldDateReceived = inputRecord.getOld().getValue(QCM_DATE_RECEIVED, ValueType.DATE);
						LocalDate oldDateCompleted = inputRecord.getOld().getValue(QCM_DATE_COMPLETED, ValueType.DATE);
						if (oldDateReceived != null && newDateReceived != null && newDateReceived.isBefore(oldDateReceived)) {
							newDateReceived = oldDateReceived;
							inputRecord.getNew().setValue(QCM_DATE_RECEIVED, newDateReceived);
						}
						if (newDateReceived != null && newDateCompleted != null && (!newDateReceived.equals(oldDateReceived) || !newDateCompleted.equals(oldDateCompleted))) {
							BigDecimal daysFinalToCompleted = null;
							daysFinalToCompleted = calculateTurnaroundTime(newDateReceived, newDateCompleted);
							inputRecord.getNew().setValue(QCM_DAYS_FINAL_TO_COMPLETED,daysFinalToCompleted);
							inputRecord.getNew().setValue(QCM_ON_TIME,isOnTime(daysFinalToCompleted));
						}
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
		while (dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
			dateReceived = dateReceived.plusDays(1);
		}
		//We now have adjusted dateReceived to be used in calculation
		int businessDayCount = 0;
		while (dateReceived.isBefore(dateCompleted)) {
			if (dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SATURDAY) || dateReceived.getDayOfWeek().equals(java.time.DayOfWeek.SUNDAY)) {
				//Set dateReceived forward 1 day
				dateReceived = dateReceived.plusDays(1);
			} else {
				dateReceived = dateReceived.plusDays(1);
				businessDayCount++;
			}
		}
		dayCount = new BigDecimal(businessDayCount);
		return dayCount;
	}
}