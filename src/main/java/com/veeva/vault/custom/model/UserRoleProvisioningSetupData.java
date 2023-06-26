/*
 * --------------------------------------------------------------------
 * UserDefinedClass: UserRoleProvisioningSetupData
 * Author: Todd Taylor
 * Created Date:        2017-07-27
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:	A class that represents a User Role Provisioning Setup Data
 *---------------------------------------------------------------------
 * Revision:
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved inner class from UserRoleProvisioningChanges to it's own class
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.model;

import com.veeva.vault.custom.services.VpsUserRoleTemplateProvisionService;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inner class for the user role provisioning setup data
 *
 */
@UserDefinedClassInfo
public class UserRoleProvisioningSetupData {
    private String templateGroup;
    private String objectName; // object name of the user role setup object (the label)
    private String objectNamePL; // object name of the user role setup object (the picklist)
    private String userAPIName; // the user field api name on the user role setup
    private String countryAPIName; // optional - api name of the country field on user role setup object
    private List<UserRoleTemplateMapping> urtm;

    private static final int BATCH_SIZE = 500;

    public UserRoleProvisioningSetupData(String templateGroup) {
        String SINGLE_QUOTE = String.valueOf((char) 39);

        urtm = VaultCollections.newList();

        setTemplateGroup(templateGroup);
        setUserAPIName("user__v");  // always user__v on the user role setup records

        String query = "select user_role_setup_object__c,country_api_name__c from user_role_template_groups__c where id = " + SINGLE_QUOTE + templateGroup + SINGLE_QUOTE;

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(query);
        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();

            setCountryAPIName(qr.getValue("country_api_name__c", ValueType.STRING));

            List<String> listURS = qr.getValue("user_role_setup_object__c", ValueType.PICKLIST_VALUES);
            PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
            Picklist picklistURSObject = picklistService.getPicklist("user_role_setup_object__c");
            setObjectName(picklistURSObject.getPicklistValue(listURS.get(0)).getLabel());
            setObjectNamePL(listURS.get(0));
        }

        // urtm - Retrieve the user role template mapping setup data
        query = "select template_field__c,user_role_setup_field__c,is_picklist__c from user_role_template_mapping__c where user_role_setup_object__c = " + SINGLE_QUOTE + objectNamePL + SINGLE_QUOTE + " and status__v=" + SINGLE_QUOTE + "active__v" + SINGLE_QUOTE;
        queryResponse = queryService.query(query);
        iterator = queryResponse.streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();
            urtm.add(new UserRoleTemplateMapping(qr.getValue("template_field__c", ValueType.STRING),qr.getValue("user_role_setup_field__c", ValueType.STRING),qr.getValue("is_picklist__c", ValueType.BOOLEAN)));
        }
    }

    /**
     * Query user role setup and remove existing users
     *
     * @param setUsers
     * 			Set of user ids to check
     *
     * @param recTemplate
     * 			Record for the current template record
     *
     */
    public void deleteExistingRecords(Set<String> setUsers, Record recTemplate) {
        List<Record> listRecord = VaultCollections.newList();

        Map<String,UserRoleTemplateMapping> urtmf = getURTMByURSField();

        // Query all user role setup records for the current user (will later find the matches). Ignore country field.

        String query = "select id,";
        for (String s : urtmf.keySet())
            query += s + ",";

        query = query.substring(0, query.length()-1);
        query += " from " + objectName + " where " + userAPIName + " contains (";

        for (String s : setUsers) query += "'" + s + "'" + ",";
        query = query.substring(0, query.length()-1);
        query += ")";

        Log.debug("Executing VQL: " + query);
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(query);
        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

        RecordService recordService = ServiceLocator.locate(RecordService.class);

        /*
         * Loop through the user role setup records, find if there is an exact match on all sharing fields in the record
         * 1. While loop of all user role setup records
         *  2. For loop for the fields on the template to set the data
         */
        int count = 0;
        while (iterator.hasNext()) {
            QueryResult queryResult = iterator.next();
            // 2.9.1 Start - Move to UDS to unserve memories
            boolean bDelete = ServiceLocator
                    .locate(VpsUserRoleTemplateProvisionService.class)
                    .isDelete(urtmf, queryResult, recTemplate, null, null);
            // 2.9.1 End

            if (bDelete) {
                // the current template and user role setup record are exact match, lets delete it
                listRecord.add(recordService.newRecordWithId(objectName,queryResult.getValue("id", ValueType.STRING)));

                if (listRecord.size() == BATCH_SIZE) {
                    deleteRecords(listRecord);
                    listRecord = VaultCollections.newList();
                }
            }
            count++;
        }
        Log.debug("Total Existing URS Record:" + count);
        // Process records outside the batch
        if (listRecord.size() > 0 ) deleteRecords(listRecord);
    }

    public String getTemplateGroup() {
        return templateGroup;
    }

    public void setTemplateGroup(String templateGroup) {
        if (templateGroup != null) this.templateGroup = templateGroup;
        else this.templateGroup = "";
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        if (objectName != null)  this.objectName = objectName;
        else this.objectName = "";
    }

    public String getObjectNamePL() {
        return objectNamePL;
    }

    private void setObjectNamePL(String objectNamePL) {
        if (objectNamePL != null)  this.objectNamePL = objectNamePL;
        else this.objectNamePL = "";
    }

    public String getUserAPIName() {
        return userAPIName;
    }

    public void setUserAPIName(String userAPIName) {
        if (userAPIName != null) this.userAPIName = userAPIName;
        else this.userAPIName = "";
    }

    public String getCountryAPIName() {
        return countryAPIName;
    }

    public void setCountryAPIName(String countryAPIName) {
        if (countryAPIName != null) this.countryAPIName = countryAPIName;
        else this.countryAPIName = "";
    }
    /**
     * Map of the user role template with template field as key

     public Map<String, userRoleTemplateMapping> getURTMByTemplate() {
     Map<String, userRoleTemplateMapping> m = VaultCollections.newMap();
     for (userRoleTemplateMapping u : urtm)
     m.put(u.getTemplate_field__c(),u);

     return m;
     }
     */
    public Map<String, UserRoleTemplateMapping> getURTMByURSField() {
        Map<String, UserRoleTemplateMapping> m = VaultCollections.newMap();
        for (UserRoleTemplateMapping u : urtm)
            m.put(u.getUser_role_setup_field(),u);

        return m;
    }
    /**
     * Perform a DML save operation on a list of records.
     * Rollback the entire transaction when encountering errors.
     *
     * @param listRecord
     *            - list of records to delete
     */
    private void deleteRecords(List<Record> listRecord) {
        Log.debug("Deleting records of size: " + listRecord.size());
        RecordService recordService = ServiceLocator.locate(RecordService.class);

        recordService.batchDeleteRecords(listRecord)
                .onErrors(batchOperationErrors -> {
                    batchOperationErrors.stream().findFirst().ifPresent(error -> {
                        String errMsg = error.getError().getMessage();
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
                    });
                })
                .execute();
    }
}