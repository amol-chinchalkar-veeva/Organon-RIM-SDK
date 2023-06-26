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

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.group.GroupService;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;
import com.veeva.vault.sdk.api.query.Query;
import com.veeva.vault.sdk.api.query.QueryExecutionRequest;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.workflow.WorkflowInstanceService;
import com.veeva.vault.sdk.api.workflow.WorkflowParticipantGroup;
import com.veeva.vault.sdk.api.workflow.WorkflowParticipantGroupUpdate;

import java.util.List;
import java.util.Map;
import java.util.Set;

@UserDefinedServiceInfo
public class VpsDispatchHelperImpl implements VpsDispatchHelperService {

    //    private static final String DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX = "_country_dispatch_group__c";
    private static final String DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX = " Country Dispatch Group";


    /**
     * *
     *
     * @param workflowInstanceService
     * @param participantGroup
     * @param dispatchCountriesLabelSet
     */
    public void setParticipants(WorkflowInstanceService workflowInstanceService,
                                WorkflowParticipantGroup participantGroup, List<String> dispatchCountriesLabelSet, Map<String, String> groupNamesLabel) {

        GroupService groupService = ServiceLocator.locate(GroupService.class);
        List<Group> participantGroups = VaultCollections.newList();
        LogService logger = ServiceLocator.locate(LogService.class);
        boolean debug = logger.isDebugEnabled();
        for (String item : dispatchCountriesLabelSet) {
            if (groupNamesLabel.containsKey(item)) {
                String groupAPIName = groupNamesLabel.get(item);
                if(debug) logger.debug("Group API Name:{}", groupAPIName);
                Group dispatchCountryGroup = groupService
                        .getGroupsByNames(VaultCollections.asList(groupAPIName))
                        .getGroupByName(groupAPIName);
                participantGroups.add(dispatchCountryGroup);
            } else {
                throw new RollbackException("SET_PARTICIPANT_ERROR:", "User Managed Group " + item + "  is missing.");
            }
        }

        WorkflowParticipantGroupUpdate participantGroupUpdate =
                workflowInstanceService.newParticipantGroupUpdate(participantGroup)
                        .setGroups(participantGroups);
        workflowInstanceService.updateParticipantGroup(participantGroupUpdate);
    }

    /**
     *
     * @param dispatchCountriesLabelSet
     */
    public void printSet(List<String> dispatchCountriesLabelSet) {
        LogService logger = ServiceLocator.locate(LogService.class);
        for (String str : dispatchCountriesLabelSet) {
            logger.debug("Values:{}", str);
        }
    }

//    /**
//     * *
//     *
//     * @param workflowInstanceService
//     * @param participantGroup
//     * @param dispatchCountriesLabelSet
//     */
//    public void setParticipant1s(WorkflowInstanceService workflowInstanceService,
//                                 WorkflowParticipantGroup participantGroup, Set<String> dispatchCountriesLabelSet, Map<String, String> groupNamesLabel) {
//
//        GroupService groupService = ServiceLocator.locate(GroupService.class);
//        List<Group> participantGroups = VaultCollections.newList();
//        LogService logger = ServiceLocator.locate(LogService.class);
//        logger.debug("***********dispatchCountriesLabelSet>>", dispatchCountriesLabelSet.size());
//        logger.debug("dispatchCountriesLabel Print Set Size:******************{}", dispatchCountriesLabelSet);
//        for (String str : dispatchCountriesLabelSet) {
//            logger.debug("str____________>", str);
//        }
//
//
//        dispatchCountriesLabelSet.stream().forEach(countryPicklistLabel -> {
//            //String groupLabel = countryPicklistLabel.concat(DISPATCH_COUNTRIES_GROUP_NAME_SUFFIX).trim();
//            logger.debug("groupLabel", countryPicklistLabel);
//            if (groupNamesLabel.containsValue(countryPicklistLabel)) {
//                String groupAPIName = groupNamesLabel.get(countryPicklistLabel);
//                logger.debug("***********groupAPIName in Helper{}", groupAPIName);
//                Group dispatchCountryGroup = groupService
//                        .getGroupsByNames(VaultCollections.asList(groupAPIName))
//                        .getGroupByName(groupAPIName);
//                participantGroups.add(dispatchCountryGroup);
//            } else {
//                throw new RollbackException("SET_PARTICIPANTS", "User Managed Group " + countryPicklistLabel + "  is missing.");
//            }
//        });
//        WorkflowParticipantGroupUpdate participantGroupUpdate =
//                workflowInstanceService.newParticipantGroupUpdate(participantGroup)
//                        .setGroups(participantGroups);
//        workflowInstanceService.updateParticipantGroup(participantGroupUpdate);
//    }

    /**
     * *
     *
     * @param groupType
     * @return
     */
    public List<String> getGroupNames(String groupType) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        LogService logger = ServiceLocator.locate(LogService.class);
        List<String> groupNameList = VaultCollections.newList();

        List<String> fields = VaultCollections.newList();
        fields.add("name__v");

        Query query = queryService.newQueryBuilder()
                .withSelect(fields)
                .withFrom("group__sys")
                .withWhere("type__sys = '" + groupType + "' AND status__v='active__v'")
                .build();

        QueryExecutionRequest queryExecutionRequest = queryService.newQueryExecutionRequestBuilder().withQuery(query).build();
        queryService.query(queryExecutionRequest)
                .onError(queryOperationError -> {
                    logger.debug("Query error message: " + queryOperationError.getMessage());
                    logger.debug("Query: " + queryOperationError.getQueryString());
                })
                .onSuccess(queryExecutionResponse -> {

                    queryExecutionResponse.streamResults().forEach(queryExecutionResult -> {
                        groupNameList.add(queryExecutionResult.getValue("name__v", ValueType.STRING));
                    });
                })
                .execute();
        return groupNameList;
    }

    /**
     * *
     *
     * @param groupType
     * @return
     */
    public Map<String, String> getGroupLabels(String groupType) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        LogService logger = ServiceLocator.locate(LogService.class);
        Map<String, String> groupNameList = VaultCollections.newMap();

        List<String> fields = VaultCollections.newList();
        fields.add("name__v");
        fields.add("label__v");

        Query query = queryService.newQueryBuilder()
                .withSelect(fields)
                .withFrom("group__sys")
                .withWhere("type__sys = '" + groupType + "' AND status__v='active__v'")
                .build();

        QueryExecutionRequest queryExecutionRequest = queryService.newQueryExecutionRequestBuilder().withQuery(query).build();
        queryService.query(queryExecutionRequest)
                .onError(queryOperationError -> {
                    logger.debug("Query error message: " + queryOperationError.getMessage());
                    logger.debug("Query: " + queryOperationError.getQueryString());
                })
                .onSuccess(queryExecutionResponse -> {

                    queryExecutionResponse.streamResults().forEach(queryExecutionResult -> {
                        groupNameList.put(queryExecutionResult.getValue("label__v", ValueType.STRING).trim(), queryExecutionResult.getValue("name__v", ValueType.STRING).trim());
                    });
                })
                .execute();
        return groupNameList;
    }

    /**
     * *
     *
     * @param groupType
     * @return
     */
    public Map<String, String> getGroupNameLabels(String groupType) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        LogService logger = ServiceLocator.locate(LogService.class);
        Map<String, String> groupNameList = VaultCollections.newMap();

        List<String> fields = VaultCollections.newList();
        fields.add("name__v");
        fields.add("label__v");

        Query query = queryService.newQueryBuilder()
                .withSelect(fields)
                .withFrom("group__sys")
                .withWhere("type__sys = '" + groupType + "' AND status__v='active__v'")
                .build();

        QueryExecutionRequest queryExecutionRequest = queryService.newQueryExecutionRequestBuilder().withQuery(query).build();
        queryService.query(queryExecutionRequest)
                .onError(queryOperationError -> {
                    logger.debug("Query error message: " + queryOperationError.getMessage());
                    logger.debug("Query: " + queryOperationError.getQueryString());
                })
                .onSuccess(queryExecutionResponse -> {

                    queryExecutionResponse.streamResults().forEach(queryExecutionResult -> {
                        groupNameList.put(queryExecutionResult.getValue("label__v", ValueType.STRING), queryExecutionResult.getValue("name__v", ValueType.STRING));
                    });
                })
                .execute();
        return groupNameList;
    }

    public String translateGroupNameString(String input) {
        //add the suffix
        String inputWithSuffix = input.concat(" Country Dispatch Group");
        // Remove single quotes, commas, and parentheses
        //String removed = inputWithSuffix.replaceAll("[',()]", "");
        String removed = StringUtils.replaceAll(inputWithSuffix, "[',()]", "");
        // Replace spaces with underscores
        //String replaced = removed.replaceAll("\\s", "_");
        String replaced = StringUtils.replaceAll(removed, "\\s", "_");
        // Truncate the string to a maximum of 39 characters
        String truncated = replaced.substring(0, Math.min(replaced.length(), 39));
        if (truncated.endsWith("_")) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        // Convert to lowercase and append "__c" at the end
        String result = truncated.toLowerCase() + "__c";

        return result;
    }

    /**
     * Returns the  picklist label as Set. if no items exists, returns null
     *
     * @param inputRecord       Record
     * @param picklistFieldName picklist name
     * @return picklistlabels as Set
     */
    public Set<String> getPicklistLabels(Record inputRecord, String picklistFieldName) {
        LogService log = ServiceLocator.locate(LogService.class);

        PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
        Picklist picklist = picklistService.getPicklist(picklistFieldName);
        List<String> picklistValues = inputRecord.getValue(picklistFieldName, ValueType.PICKLIST_VALUES);

        Set<String> picklistLabels = VaultCollections.newSet();
        if (picklistValues != null && !picklistValues.isEmpty()) {

            for (String picklistValue : picklistValues) {
                //Get picklist value label
                String picklistValueLabel =
                        picklist.getPicklistValue(picklistValue).getLabel();
                picklistLabels.add(picklistValueLabel);
            }
        }

        return picklistLabels;
    }
}