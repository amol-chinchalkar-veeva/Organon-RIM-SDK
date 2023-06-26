package com.veeva.vault.custom.services;

import com.veeva.vault.custom.util.api.VpsAPIResponse;
import com.veeva.vault.custom.jobs.VpsUserRoleTemplateJob;
import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.custom.model.UserRoleTemplateMapping;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;
import com.veeva.vault.sdk.api.query.QueryCountRequest;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.List;
import java.util.Set;

@UserDefinedServiceInfo
public class VpsUserRoleProvisioningSetupDataServiceImpl implements VpsUserRoleProvisioningSetupDataService {
    private static final int BATCH_SIZE = 500;

    @Override
    public void initTemplateGroup(TemplateGroup templateGroup) {
        String RESPONSE_STATUS_SUCCESS = "SUCCESS";
        String LOCAL_CONNECTION = "local_http_callout_connection";
        String URL_QUERY = "/api/v21.2/query";

        LogService debug = ServiceLocator.locate(LogService.class);
        debug.logResourceUsage("VpsUserRoleProvisioningSetupDataServiceImpl.initTemplateGroup");

        templateGroup.setUrtm(VaultCollections.newList());

        String query = "select user_role_setup_object__c,country_api_name__c from user_role_template_groups__c where id = '" + templateGroup.getTemplateGroupId() + "'";

        PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
        Picklist picklistURSObject = picklistService.getPicklist("user_role_setup_object__c");

        HttpService httpService = ServiceLocator.locate(HttpService.class);
        HttpRequest ursTemplateGroupRequest = httpService.newHttpRequest(LOCAL_CONNECTION)
                .setMethod(HttpMethod.POST)
                .setBodyParam("q", query)
                .appendPath(URL_QUERY);

        httpService.send(ursTemplateGroupRequest, HttpResponseBodyValueType.STRING)
                .onSuccess(httpResponse -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(httpResponse.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSE_STATUS_SUCCESS)) {
                        JsonArray jsonArray = apiResponse.getArray("data");
                        if (jsonArray != null) {
                            for (int i = 0; i < jsonArray.getSize(); i++) {
                                JsonObject dataItem = jsonArray.getValue(i, JsonValueType.OBJECT);
                                String countryApiName = dataItem.getValue("country_api_name__c", JsonValueType.STRING);
                                JsonArray listURS = dataItem.getValue("user_role_setup_object__c", JsonValueType.ARRAY);
                                String urs = listURS.getValue(0, JsonValueType.STRING);

                                templateGroup.setCountryAPIName(countryApiName);
                                templateGroup.setObjectName(picklistURSObject.getPicklistValue(urs).getLabel());
                                templateGroup.setObjectNamePL(urs);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();

        // urtm - Retrieve the user role template mapping setup data
        query = "select template_field__c, user_role_setup_field__c, is_picklist__c from user_role_template_mapping__c where user_role_setup_object__c = '" + templateGroup.getObjectNamePL() + "' and status__v = 'active__v'";

        HttpRequest ursTemplateMappingRequest = httpService.newHttpRequest(LOCAL_CONNECTION)
                .setMethod(HttpMethod.POST)
                .setBodyParam("q", query)
                .appendPath(URL_QUERY);

        httpService.send(ursTemplateMappingRequest, HttpResponseBodyValueType.STRING)
                .onSuccess(httpResponse -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(httpResponse.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSE_STATUS_SUCCESS)) {
                        JsonArray jsonArray = apiResponse.getArray("data");
                        if (jsonArray != null) {
                            for (int i = 0; i < jsonArray.getSize(); i++) {
                                JsonObject dataItem = jsonArray.getValue(i, JsonValueType.OBJECT);
                                String templateField = dataItem.getValue("template_field__c", JsonValueType.STRING);
                                String ursField = dataItem.getValue("user_role_setup_field__c", JsonValueType.STRING);
                                Boolean isPicklist = dataItem.getValue("is_picklist__c", JsonValueType.BOOLEAN);
                                templateGroup.getUrtm().add(new UserRoleTemplateMapping(templateField, ursField, isPicklist));
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();
    }

    @Override
    public void deleteExistingRecords(TemplateGroup templateGroup, Set<String> setUsers, Record recTemplate) {
        // Query all user role setup records for the current user (will later find the matches). Ignore country field.
        StringBuilder query = new StringBuilder()
                .append("select id from ")
                .append(templateGroup.getObjectName())
                .append(" where ")
                .append(templateGroup.getUserAPIName())
                .append(" contains ('")
                .append(String.join("','", setUsers))
                .append("')");

        long resultCount = getQueryResultCount(query.toString());

        if (resultCount <= 500) {
            startUserRoleTemplateJob(query.toString(), templateGroup);
        }
        else {
            for (long x = 0; x < resultCount; x+=500) {
                StringBuilder jobQuery = new StringBuilder(query)
                        .append(" SKIP ").append(x)
                        .append(" PAGESIZE 500");
                startUserRoleTemplateJob(jobQuery.toString(), templateGroup);
            }
        }

        /*JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters(VpsUserRoleTemplateJob.JOB_NAME);
        jobParameters.setValue("query", query.toString());
        jobParameters.setValue("template_group_object_name", templateGroup.getObjectName());
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

    public void startUserRoleTemplateJob(String query, TemplateGroup templateGroup) {
        JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters(VpsUserRoleTemplateJob.JOB_NAME);
        jobParameters.setValue("query", query);
        jobParameters.setValue("template_group_object_name", templateGroup.getObjectName());
        jobService.run(jobParameters);
    }


    /**
     * Perform a DML save operation on a list of records.
     * Rollback the entire transaction when encountering errors.
     *
     * @param listRecord
     *            - list of records to delete
     */
    private void deleteRecords(List<Record> listRecord) {
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

    /**
     * Get a list of active users for the current template group
     *
     * @param templateGroup
     * 			Current template group
     *
     * @return set of user ids for the template group
     */
    @Override
    public Set<String> getCurrentUsers(String templateGroup) {
        String RESPONSE_STATUS_SUCCESS = "SUCCESS";
        String LOCAL_CONNECTION = "local_http_callout_connection";
        String URL_QUERY = "/api/v21.2/query";

        Set<String> setUsers = VaultCollections.newSet();
        String query = "select user__c from user_role_template_assignment__c where template_group__c = '" + templateGroup + "' and status__v = 'active__v'";

        HttpService httpService = ServiceLocator.locate(HttpService.class);
        HttpRequest request = httpService.newHttpRequest(LOCAL_CONNECTION)
                .setMethod(HttpMethod.POST)
                .setBodyParam("q", query)
                .appendPath(URL_QUERY);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onSuccess(httpResponse -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(httpResponse.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSE_STATUS_SUCCESS)) {
                        JsonArray jsonArray = apiResponse.getArray("data");
                        if (jsonArray != null) {
                            for (int i = 0; i < jsonArray.getSize(); i++) {
                                JsonObject dataItem = jsonArray.getValue(i, JsonValueType.OBJECT);
                                String userId = dataItem.getValue("user__c", JsonValueType.STRING);
                                setUsers.add(userId);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();

        // max 10k
        return setUsers;
    }
}