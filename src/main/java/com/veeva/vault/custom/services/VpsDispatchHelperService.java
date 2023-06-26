/*
 * --------------------------------------------------------------------
 * UserDefinedService:	VpsDispatchHelper
 * Author:				Amol Chinchalkar @ Veeva
 * Date:				2023-01-27
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2023 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 * 		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.services;

import com.veeva.vault.sdk.api.core.UserDefinedService;
import com.veeva.vault.sdk.api.core.UserDefinedServiceInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.workflow.WorkflowInstanceService;
import com.veeva.vault.sdk.api.workflow.WorkflowParticipantGroup;

import java.util.List;
import java.util.Map;
import java.util.Set;


@UserDefinedServiceInfo
public interface VpsDispatchHelperService extends UserDefinedService {

   void setParticipants(WorkflowInstanceService workflowInstanceService,
                        WorkflowParticipantGroup participantGroup, List<String> dispatchCountriesLabelSet, Map<String,String> groupNamesLabel);

    Map<String, String> getGroupNameLabels(String groupType);
    Set<String> getPicklistLabels(Record inputRecord, String picklistFieldName);

    void printSet(List<String> dispatchCountriesLabelSet);
}
