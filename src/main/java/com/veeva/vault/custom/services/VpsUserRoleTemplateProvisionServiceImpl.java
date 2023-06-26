/*
 * --------------------------------------------------------------------
 * UserDefinedService: VpsUserRoleTemplateProvisionServiceImpl
 * Author: bryanchan @ Veeva
 * Created Date:        2020-08-27
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:	Implementation of the User Role Template Service
 *---------------------------------------------------------------------
 * Revision:
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved the exact same logic from UserRoleTemplateAssignmentProvision
 *    and UserRoleTemplateProvisionChanges to UDS to free up memory limits.
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.services;

import com.veeva.vault.custom.jobs.VpsUserRoleTemplateAssignmentJob;
import com.veeva.vault.custom.model.UserRoleTemplateMapping;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;
import com.veeva.vault.sdk.api.query.*;

import java.util.List;
import java.util.Map;


@UserDefinedServiceInfo
public class VpsUserRoleTemplateProvisionServiceImpl implements VpsUserRoleTemplateProvisionService {

    private static final int BATCH_SIZE = 500;

    /**
     * Determines if a record should be deleted from User Role Template Provisioning
     *
     * @param urtmf User Role Template Mapping
     * @param queryResult - query results
     * @param recTemplate - the record template
     * @param countryAPIName - the country api name
     * @param countryValue - the country value
     * @return true if the record can be deleted.
     */
    @Override
    public boolean isDelete(Map<String, UserRoleTemplateMapping> urtmf,
                            QueryResult queryResult, Record recTemplate, String countryAPIName, String countryValue) {

        return true;

        /*
         *  Go through all templates, compare the fields, and determine if a match.
         *  Start out with a delete of the record, flag as false when one delta exists.
         *  To limit next loop, first check for role match (no delete if no role match)
         */
        /*boolean bDelete = true;
        for (UserRoleTemplateMapping u : urtmf.values()) {
            if (u.is_picklist__c()) {
                String p1 = TriggerUtil.getFirstStringFromList(queryResult
                        .getValue(u.getUser_role_setup_field(), ValueType.PICKLIST_VALUES));

                String p2 = TriggerUtil.getFirstStringFromList(queryResult
                        .getValue(u.getTemplate_field__c(), ValueType.PICKLIST_VALUES));

                bDelete = p1.equals(p2);
            }
            else {
                String s1 = queryResult.getValue(u.getUser_role_setup_field(), ValueType.STRING);
                String s2 = recTemplate.getValue(u.getTemplate_field__c(), ValueType.STRING);

                if (countryAPIName != null && !countryAPIName.equals("") && u.getUser_role_setup_field().equals(countryAPIName)) {
                    // country at the user level
                    if (!s1.equals(countryValue)) bDelete = false;
                }
                else {
                    // country at the template level
                    if (s1 == null && s2 != null) bDelete = false;
                    else if (s1 != null && s2 == null) bDelete = false;
                    else if (s1 != null && s2 != null) {
                        if (!s1.equals(s2)) bDelete = false;
                    }
                }
            }
            // 2.9.1 This line was not included for UserRoleProvisioningSetupData, but it should
            if (!bDelete) break; // break from field level loop, theres at least one delta
        }

        return bDelete;*/
    }

    public List<Record> getNewUserRecords(Record recContext, List<UserRoleTemplateMapping> urtm,
                                          List<Record> templates, String objectName, String userAPIName) {
        List<Record> listRecord = VaultCollections.newList();
        RecordService recordService = ServiceLocator.locate(RecordService.class);

        for (Record recTemplate : templates) {
            Record r = recordService.newRecord(objectName);

            String country = recContext.getValue("country__c", ValueType.STRING);

            r.setValue(userAPIName, recContext.getValue("user__c", ValueType.STRING));

            for (UserRoleTemplateMapping u : urtm) {
                if (u.is_picklist__c())
                    r.setValue(u.getUser_role_setup_field(), recTemplate.getValue(u.getTemplate_field__c(), ValueType.PICKLIST_VALUES));
                else
                    r.setValue(u.getUser_role_setup_field(), recTemplate.getValue(u.getTemplate_field__c(), ValueType.STRING));
            }

            // set country last to avoid country at the template from above (2 places to set country)
            if (country != null && !country.equals(""))
                r.setValue(recContext.getValue("country_api_name__c", ValueType.STRING), country);

            listRecord.add(r);
        }

        return listRecord;
    }

    /**
     * Refresh all template group records
     *
     * @param templateGroup
     * 			Current template group
     */
    public void refreshUserProvisioningRecords(String templateGroup) {
        String query = "select id from user_role_template_assignment__c where template_group__c = '" + templateGroup + "' and status__v = 'active__v'";

        long resultCount = getQueryResultCount(query);

        if (resultCount <= 500) {
            startUserRoleTemplateAssignmentJob(query);
        }
        else {
            for (long x = 0; x < resultCount; x+=500) {
                StringBuilder jobQuery = new StringBuilder(query)
                        .append(" SKIP ").append(x)
                        .append(" PAGESIZE 500");
                startUserRoleTemplateAssignmentJob(jobQuery.toString());
            }
        }

        /*JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters(VpsUserRoleTemplateAssignmentJob.JOB_NAME);
        jobParameters.setValue("query", query);
        jobService.run(jobParameters);*/
    }

    public long getQueryResultCount(String query) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryCountRequest queryCountRequest = queryService
                .newQueryCountRequestBuilder()
                .withQueryString(query)
                .build();

        final long[] queryResultCount = {0};

        queryService.count(queryCountRequest)
                .onSuccess(queryCountResponse -> {
                    queryResultCount[0] = queryCountResponse.getTotalCount();
                })
                .onError(queryOperationError -> {
                   //String err = queryOperationError.getMessage();
                })
                .execute();

        return queryResultCount[0];
    }

    public void startUserRoleTemplateAssignmentJob(String query) {
        JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters(VpsUserRoleTemplateAssignmentJob.JOB_NAME);
        jobParameters.setValue("query", query);
        jobService.run(jobParameters);
    }

    /**
     * Perform a DML save operation on a list of records.
     * Rollback the entire transaction when encountering errors.
     *
     * @param listRecord
     *            - list of records to perform the save upon
     */
    private void saveRecords(List<Record> listRecord) {
        RecordService recordService = ServiceLocator.locate(RecordService.class);

        recordService.batchSaveRecords(listRecord)
                .onErrors(batchOperationErrors -> {
                    batchOperationErrors.stream().findFirst().ifPresent(error -> {
                        String errMsg = error.getError().getMessage();
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
                    });
                })
                .execute();
    }

}