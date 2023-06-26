/*
 * --------------------------------------------------------------------
 * RecordWorklowAction:	VpsAssignDispatchRecipients
 * Object:				dispatch__c
 * Author:				Amol Chinchalkar @ Veeva
 * Date:				2022-12-20
 *---------------------------------------------------------------------
 * Description: Assign Dispatch Recipients
 *---------------------------------------------------------------------
 * Copyright (c) 2022 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.actions;

import com.veeva.vault.custom.services.VpsDispatchHelperService;
import com.veeva.vault.custom.util.RecordServiceUtil;
import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.workflow.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RecordWorkflowActionInfo(label = "Assign Dispatch Recipients Start",
        stepTypes = {WorkflowStepType.START})
public class VpsAssignDispatchRecipientsStartStep implements RecordWorkflowAction {


    private static final String DISPATCH_COUNTRIES = "dispatch_countries__c";
    private static final String SUBMISSION_OBJ = "submission__v";
    //private static final String DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX = "_country_dispatch_group__c";
    private static final String DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX = " Country Dispatch Group";
    private static final String USER_MANAGED_GROUP = "user_managed__sys";

    public void execute(RecordWorkflowActionContext context) throws RollbackException {
        LogService log = ServiceLocator.locate(LogService.class);
        boolean debug = log.isDebugEnabled();
        boolean groupMatch = false;
        if (debug) log.debug("[ENTRY] VpsAssignDispatchRecipientsStartStep");

        WorkflowEvent event = context.getEvent();
        WorkflowInstanceService workflowInstanceService = ServiceLocator.locate(WorkflowInstanceService.class);
        WorkflowParticipantGroup participantGroup = context.getParticipantGroup();
        List<Record> recordList = VaultCollections.newList();
        VpsDispatchHelperService dispatchHelper = ServiceLocator.locate(VpsDispatchHelperService.class);
        List<Record> workflowRecords = context.getRecords();
        Map<String, String> groupNamesLabel = VaultCollections.newMap();
        Set<String> dispatchCountriesLabel = VaultCollections.newSet();
        List<String> dispatchCountriesGroupLabel = VaultCollections.newList();
        // Process Submission Records

        //get valid dispatch groups
        groupNamesLabel = dispatchHelper.getGroupNameLabels(USER_MANAGED_GROUP);


        for (Record submissionRecord : workflowRecords) {
            if (event == WorkflowEvent.AFTER_CREATE) {
                dispatchCountriesLabel = dispatchHelper.getPicklistLabels(submissionRecord, DISPATCH_COUNTRIES);

                for (String item : dispatchCountriesLabel) {
                    //String groupAPIName = StringUtils.replaceAll(item.toLowerCase(), " ", "_").concat(DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX);
                    String groupLabel = item.concat(DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX);
                    dispatchCountriesGroupLabel.add(groupLabel);
                    if (groupNamesLabel.containsKey(groupLabel)) {
                        if (debug) log.debug("Group Present:{}", groupLabel);
                        groupMatch = true;

                    } else {
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "User Managed Group " + groupLabel + "  is missing.");
                    }
                }
                if (groupMatch) {
                    dispatchHelper.setParticipants(workflowInstanceService, participantGroup, dispatchCountriesGroupLabel, groupNamesLabel);
                }
            }
            //Very important step (Removed from SubmissionDispatchHandler & added here)
            submissionRecord.setValue(DISPATCH_COUNTRIES, null);
            recordList.add(submissionRecord);
        }
        // Bulk saves the records
        if (recordList.size() > 0) {
            RecordServiceUtil.updateRecordList(recordList, SUBMISSION_OBJ);
        }
        if (debug) log.debug("[Exit] VpsAssignDispatchRecipientsStartStep");
    }


}