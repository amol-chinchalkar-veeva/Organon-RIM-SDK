/*
 * --------------------------------------------------------------------
 * UDC:         VpsAPIClient
 * Author:      achinchalkar @ Veeva
 * Date:        2019-07-25
 * --------------------------------------------------------------------
 * Description: Helper for running vql queries via api
 * --------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 * This code is based on pre-existing content developed and
 * owned by Veeva Systems Inc. and may only be used in connection
 * with the deliverable with which it was provided to Customer.
 * --------------------------------------------------------------------
 */
package com.veeva.vault.custom.util.api;


import com.veeva.vault.custom.util.VpsBaseHelper;
import com.veeva.vault.custom.util.VpsUtilHelper;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@UserDefinedClassInfo
public class VpsAPIClient extends VpsBaseHelper {

    private static final String APIFIELD_ACTIONS = "lifecycle_actions__v";
    private static final String APIFIELD_ASSIGNED_GROUPS = "assignedGroups";
    private static final String APIFIELD_ASSIGNED_USERS = "assignedUsers";
    private static final String APIFIELD_DOCUMENT_ROLES = "documentRoles";
    private static final String APIFIELD_FROM_TEMPLATE = "fromTemplate";
    private static final String APIFIELD_LABEL = "label__v";
    private static final String APIFIELD_NAME = "name__v";
    private static final String APIFIELD_ERROR_MESSAGE = "message";
    private static final String APIFIELD_ERROR_TYPE = "type";
    private static final String APIFIELD_QUERY = "q";
    private static final String PROCESS_ERROR = "ERROR";
    private static final String PROCESS_RACECONDITION = "RACE_CONDITION";
    private static final String PROCESS_SUCCESS = "SUCCESS";
    private static final String RESPONSESTATUS_SUCCESS = "SUCCESS";
    private static final int RETRY_ATTEMPTS_COUNT = 5;
    private static final String SDK_EXTERNAL_ID = "VpsAPIClient";
    private static final String SETTING_APIVERSION = "api_version";
    private static final String URL_BINDER_CREATETEMPLATE = "/api/%s/objects/binders";
    private static final String URL_DOCUMENT_CREATETEMPLATE = "/api/%s/objects/documents";
    private static final String URL_OBJECT_CREATE = "/api/%s/vobjects/%s";
    private static final String URL_UPDATE_DOCUMENT_VERSION = "/api/%s/objects/documents/%s/versions/%s/%s";
    private static final String URL_UPDATE_BINDER_VERSION = "/api/%s/objects/binders/%s/versions/%s/%s";
    private static final String URL_DOCUMENT_LIFEYCLEACTIONS = "/api/%s/objects/documents/%s/versions/%s/%s/lifecycle_actions/";
    private static final String URL_INITIATE_DOCUMENT_LIFEYCLEACTIONS = "/api/%s/objects/documents/%s/versions/%s/%s/lifecycle_actions/%s";
    private static final String URL_INITIATE_OBJECT_ACTION = "/api/%s/vobjects/%s/%s/actions/%s";
    private static final String URL_QUERY = "/api/%s/query";
    private static final String URL_ROLES = "/api/%s/objects/documents/%s/roles/%s";
    private static final String URL_RETRIEVE_DOCUMENT_VERSIONS = "/api/%s/objects/documents/%s/versions";


    HttpService httpService = ServiceLocator.locate(HttpService.class);
    String apiVersion = "v19.1";
    String apiConnection;
   // VpsSettingRecord sdkSettings;

    /**
     * Class to assist in making using Vault Query Language
     */
    public VpsAPIClient(String apiConnection) {
        super();

        this.apiConnection = apiConnection;

//		VpsSettingHelper settingHelper = new VpsSettingHelper(SDK_EXTERNAL_ID,true);
//		sdkSettings = settingHelper.items().get(SDK_EXTERNAL_ID);
//		if (sdkSettings != null) {
//			apiVersion = sdkSettings.getValue(SETTING_APIVERSION, apiVersion);
//		}
    }

    public Boolean createBinderFromTemplate(String templateName, Map<String, String> documentMetadata) {
        return createDocumentFromTemplate(templateName, documentMetadata, true);
    }

    public Boolean createDocumentFromTemplate(String templateName, Map<String, String> documentMetadata) {
        return createDocumentFromTemplate(templateName, documentMetadata, false);
    }

    private Boolean createDocumentFromTemplate(String templateName,
                                               Map<String, String> documentMetadata,
                                               Boolean isBinder) {

        List<Boolean> successList = VaultCollections.newList();

        String createTemplateUrl;
        if (isBinder) {
            createTemplateUrl = String.format(URL_BINDER_CREATETEMPLATE, apiVersion);
        } else {
            createTemplateUrl = String.format(URL_DOCUMENT_CREATETEMPLATE, apiVersion);
        }
        getLogService().info("createDocumentFromTemplate {}", createTemplateUrl);

        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .setMethod(HttpMethod.POST)
                .setBodyParam(APIFIELD_FROM_TEMPLATE, templateName)
                .appendPath(createTemplateUrl);

        for (String fieldName : documentMetadata.keySet()) {
            String fieldValue = documentMetadata.get(fieldName);
            request.setBodyParam(fieldName, fieldValue);
        }

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("createDocumentFromTemplate {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("createDocumentFromTemplate {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return successList.size() > 0;
    }

    /**
     * @param objectType
     * @param fieldsToUpdate
     */
    public String createObject(String objectType, Map<String, String> fieldsToUpdate) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        final String[] usertaskId = {""};
        List<String> usertaskIdList = VaultCollections.newList();

        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.POST);
        String createObjectPath = String.format(
                URL_OBJECT_CREATE,
                apiVersion,
                objectType);
        request.appendPath(createObjectPath);

        for (String key : fieldsToUpdate.keySet()) {
            request.setBodyParam(key, fieldsToUpdate.get(key));
        }
        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
                .onSuccess(httpResponse -> {
                    int responseCode = httpResponse.getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info("RESPONSE: " + httpResponse.getResponseBody());

                    JsonData response = httpResponse.getResponseBody();

                    //This API call just initiates a workflow. Log success or errors messages depending on the results of the call.
                    if (response.isValidJson()) {
                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
                        JsonObject data = response.getJsonObject().getValue("data", JsonValueType.OBJECT);
                        usertaskIdList.add(data.getValue("id", JsonValueType.STRING));
                        if (responseStatus.equals("SUCCESS")) {
                            logService.info("Starting HTTP Create Object");
                        } else {
                            logService.info("Failed to create object.");
                            if (response.getJsonObject().contains("responseMessage") == true) {
                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
                                logService.error("ERROR: {}", responseMessage);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call Out: " + responseMessage);
                            }
                            if (response.getJsonObject().contains("errors") == true) {
                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
                                logService.error("ERROR {}: {}", type, message);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call out: " + message);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {
                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info(httpOperationError.getMessage());
                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
                })
                .execute();
        if (usertaskId != null) {
            return usertaskIdList.get(0);
        } else {
            return "";
        }

    }

    public Map<String, String> getDocumentLifecycleActions(String docId,
                                                           String majorVersion,
                                                           String minorVersion) {

        Map<String, String> lifecycleActionMap = VaultCollections.newMap();

        String lifeycleActionUrl = String.format(URL_DOCUMENT_LIFEYCLEACTIONS, apiVersion, docId, majorVersion, minorVersion);
        getLogService().info("getDocumentLifecycleActions {}", lifeycleActionUrl);

        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .appendPath(lifeycleActionUrl);
        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("getDocumentLifecycleActions {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        JsonArray actionArray = apiResponse.getArray(APIFIELD_ACTIONS);
                        for (int i = 0; i < actionArray.getSize(); i++) {
                            JsonObject lifecycleAction = actionArray.getValue(i, JsonValueType.OBJECT);

                            lifecycleActionMap.put(
                                    lifecycleAction.getValue(APIFIELD_LABEL, JsonValueType.STRING),
                                    lifecycleAction.getValue(APIFIELD_NAME, JsonValueType.STRING));
                        }
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("getDocumentLifecycleActions {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }
                })
                .execute();

        return lifecycleActionMap;
    }

    public boolean initiateDocumentLifecycleActions(String docId,
                                                    String majorVersion,
                                                    String minorVersion, String lifecycleActionName) {

        String lifeycleActionUrl = String.format(URL_INITIATE_DOCUMENT_LIFEYCLEACTIONS, apiVersion, docId, majorVersion, minorVersion, lifecycleActionName);
        getLogService().info("initiateDocumentLifecycleActions {}", lifeycleActionUrl);
        List<Boolean> successList = VaultCollections.newList();

        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .appendPath(lifeycleActionUrl)
				.setMethod(HttpMethod.PUT);


        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("initiateDocumentLifecycleActions {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        getLogService().info("Initiating Lifecycle Action");
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("initiateDocumentLifecycleActions {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }
                })
                .execute();

        return successList.size() > 0;
    }


    public List<String> getDocumentUsersAndGroupsFromRole(String docId, String roleApiName) {
        List<String> usersAndGroups = VaultCollections.newList();
        String roleUrl = String.format(URL_ROLES, apiVersion, docId, roleApiName);
        getLogService().info("getDocumentUsersAndGroupsFromRole {}", roleUrl);
        List<Boolean> successList = VaultCollections.newList();
        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .appendPath(roleUrl);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("getDocumentLifecycleActions {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {

                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        JsonArray roleArray = apiResponse.getArray(APIFIELD_DOCUMENT_ROLES);

                        for (int i = 0; i < roleArray.getSize(); i++) {
                            JsonObject role = roleArray.getValue(i, JsonValueType.OBJECT);

                            JsonArray groupArray = role.getValue(APIFIELD_ASSIGNED_GROUPS, JsonValueType.ARRAY);
                            for (int g = 0; g < groupArray.getSize(); g++) {
                                usersAndGroups.add("group:" + groupArray.getValue(g, JsonValueType.NUMBER));
                            }
                            JsonArray userArray = role.getValue(APIFIELD_ASSIGNED_USERS, JsonValueType.ARRAY);
                            for (int u = 0; u < userArray.getSize(); u++) {
                                usersAndGroups.add("user:" + userArray.getValue(u, JsonValueType.NUMBER));
                            }
                        }
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("getDocumentLifecycleActions {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }
                })
                .execute();

        return usersAndGroups;
    }

    public Boolean initiateObjectAction(String objectName,
                                        String userActionName,
                                        List<String> idList) {
        return initiateObjectAction(objectName, userActionName, idList, true);
    }

    public Boolean initiateObjectAction(String objectName,
                                        String userActionName,
                                        List<String> idList,
                                        Boolean rollbackOnError) {
        for (String objectId : idList) {
            if (!initiateObjectAction(objectName, userActionName, objectId, rollbackOnError)) {
                return false;
            }
        }
        return true;
    }

    public Boolean initiateObjectAction(String objectName,
                                        String userActionName,
                                        String objectId) {
        return initiateObjectAction(objectName, userActionName, objectId, true);
    }

    public Boolean initiateObjectAction(String objectName,
                                        String userActionName,
                                        String objectId,
                                        Boolean rollbackOnError) {
        String initiateObjectActionUrl = String.format(
                URL_INITIATE_OBJECT_ACTION,
                apiVersion,
                objectName,
                objectId,
                userActionName);
        getLogService().info("initiateObjectAction {}", initiateObjectActionUrl);

        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .setMethod(HttpMethod.POST)
                .setBody("")
                .appendPath(initiateObjectActionUrl);

        //this list is used to track the results of the batch process (success vs. error)
        //errors that are non-race conditions are marked as error
        //note: using a list because lambda expressions require final variables
        Set<String> results = VaultCollections.newSet();

        //this list is used to track the number of batch attempts
        //race condition errors are retried up to RETRY_ATTEMPTS_COUNT
        //note: using a list because lambda expressions require final variables
        List<String> batchAttempts = VaultCollections.newList();

        while ((batchAttempts.size() < RETRY_ATTEMPTS_COUNT)
                && (!results.contains(PROCESS_ERROR))
                && (!results.contains(PROCESS_SUCCESS))) {
            httpService.send(request, HttpResponseBodyValueType.STRING)
                    .onError(response -> {
                        batchAttempts.add(PROCESS_ERROR);
                        results.add(PROCESS_ERROR);

                        String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                        getLogService().error("initiateObjectAction {}", errorMessage);
                        getErrorList().add(errorMessage);
                    })
                    .onSuccess(response -> {
                        VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                        if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                            batchAttempts.add(PROCESS_SUCCESS);
                            results.add(PROCESS_SUCCESS);
                        }
                        //This is HTTP 200, but an application level error
                        else {
                            JsonArray errors = apiResponse.getErrors();
                            if (errors != null) {
                                for (int i = 0; i < errors.getSize(); i++) {
                                    JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                    String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                    String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                    getLogService().error("initiateObjectAction {}", errorType + " - " + errorMessage);
                                    getErrorList().add(errorType + " - " + errorMessage);

                                    //handles race conditions for record level locking
                                    if (errorType.equals(PROCESS_RACECONDITION)) {
                                        results.add(PROCESS_RACECONDITION);
                                        sleep();

                                        //if we reached the max number of retries and rollebackOnError=true
                                        //throw rollback exception
                                        if ((batchAttempts.size() == RETRY_ATTEMPTS_COUNT) && (rollbackOnError)) {
                                            throw new RollbackException("OPERATION_NOT_ALLOWED", errorMessage + PROCESS_RACECONDITION);
                                        }
                                    } else {
                                        results.add(PROCESS_ERROR);

                                        if (rollbackOnError) {
                                            throw new RollbackException("OPERATION_NOT_ALLOWED", errorMessage);
                                        }
                                    }
                                }
                            }
                        }

                    })
                    .execute();
        }

        return results.contains(PROCESS_SUCCESS);
    }

    /**
     * Runs the current query. Query is logged to LogService
     *
     * @return QueryResponse with results from the VQL query
     */
    public VpsVQLResponse runVQL(VpsVQLRequest vpsVQLRequest) {
        vpsVQLRequest.logVQL();

        List<VpsVQLResponse> resultList = VaultCollections.newList();

        String queryUrl = String.format(URL_QUERY, apiVersion);
        getLogService().info("runVQL {}", queryUrl);

        //now call GET on the documents available user actions and build a map
        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .setMethod(HttpMethod.POST)
                .setBodyParam(APIFIELD_QUERY, vpsVQLRequest.getVQL())
                .appendPath(queryUrl);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("runVQL {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsVQLResponse vpsVQLResponse = new VpsVQLResponse(response.getResponseBody());
                    resultList.add(vpsVQLResponse);

                    JsonArray errors = vpsVQLResponse.getErrors();
                    if (errors != null) {
                        for (int i = 0; i < errors.getSize(); i++) {
                            JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                            String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                            String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                            getLogService().error("VpsVQLResponse {}", errorType + " - " + errorMessage);
                            getErrorList().add(errorType + " - " + errorMessage);
                        }
                    }

                })
                .execute();

        if (resultList.size() > 0) {
            return resultList.get(0);
        } else {
            return null;
        }
    }

    public Boolean startDocumentWorkflow(String docId,
                                         String majorVersion,
                                         String minorVersion,
                                         String lifecycleActionName,
                                         String roleName,
                                         Set<String> users,
                                         Set<String> groups) {

        List<Boolean> successList = VaultCollections.newList();

        String startWorkflowUrl = String.format(
                URL_DOCUMENT_LIFEYCLEACTIONS,
                apiVersion,
                docId,
                majorVersion,
                minorVersion) + lifecycleActionName;
        getLogService().info("startDocumentWorkflow {}", startWorkflowUrl);

        Set<String> usersAndGroups = VaultCollections.newSet();
        if (users != null) {
            for (String userId : users) {
                usersAndGroups.add("user:" + userId);
            }
        }
        if (groups != null) {
            for (String groupId : groups) {
                usersAndGroups.add("group:" + groupId);
            }
        }

        HttpRequest request = httpService.newHttpRequest(apiConnection)
                .setMethod(HttpMethod.PUT)
                .setBodyParam(roleName, VpsUtilHelper.setToString(usersAndGroups, ",", false))
                .appendPath(startWorkflowUrl);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("startDocumentWorkflow {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("startDocumentWorkflow {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return successList.size() > 0;
    }

    public Boolean startDocumentWorkflow(String docId,
                                         String majorVersion,
                                         String minorVersion,
                                         String lifecycleActionName,
                                         Map<String, String> requestParams) {

        List<Boolean> successList = VaultCollections.newList();

        String startWorkflowUrl = String.format(
                URL_DOCUMENT_LIFEYCLEACTIONS,
                apiVersion,
                docId,
                majorVersion,
                minorVersion) + lifecycleActionName;
        getLogService().info("startDocumentWorkflow {}", startWorkflowUrl);
        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.PUT);
        request.appendPath(startWorkflowUrl);
        if (requestParams != null) {
            for (String key : requestParams.keySet()) {
                request.setBodyParam(key, requestParams.get(key));
            }
        }
        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("startDocumentWorkflow {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("startDocumentWorkflow {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return successList.size() > 0;
    }

    /**
     * @param docID
     * @param majorVersion
     * @param minorVersion
     * @param documentFieldsToUpdate
     */
    public boolean updateDocumentFields(String docID, String majorVersion,
                                        String minorVersion, Map<String, String> documentFieldsToUpdate) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.PUT);
//		request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion);
//		request.appendPath(URL_DOCUMENT_UPDATE,apiVersion,docID,majorVersion,minorVersion);
        String initiateDocumentUpdateUrl = String.format(
                URL_UPDATE_DOCUMENT_VERSION,
                apiVersion,
                docID,
                majorVersion,
                minorVersion);

		request.appendPath(initiateDocumentUpdateUrl);
        for (String key : documentFieldsToUpdate.keySet()) {
            request.setBodyParam(key, documentFieldsToUpdate.get(key));
        }

		httpService.send(request, HttpResponseBodyValueType.STRING)
				.onError(response -> {
					String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
					getLogService().error("updateDocumentFields {}", errorMessage);
					getErrorList().add(errorMessage);
				})
				.onSuccess(response -> {
					VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
					if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
						successList.add(true);
					} else {
						JsonArray errors = apiResponse.getErrors();
						if (errors != null) {
							for (int i = 0; i < errors.getSize(); i++) {
								JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
								String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
								String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
								getLogService().error("updateDocumentFields {}", errorType + " - " + errorMessage);
								getErrorList().add(errorType + " - " + errorMessage);
							}
						}
					}

				})
				.execute();

		return successList.size() > 0;
    }
    /**
     * @param docID
     * @param majorVersion
     * @param minorVersion
     * @param FieldsToUpdate
     */
    public boolean updateBinderFields(String docID, String majorVersion,
                                        String minorVersion, Map<String, String> FieldsToUpdate) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.PUT);
        String initiateDocumentUpdateUrl = String.format(
                URL_UPDATE_BINDER_VERSION,
                apiVersion,
                docID,
                majorVersion,
                minorVersion);

        request.appendPath(initiateDocumentUpdateUrl);
        for (String key : FieldsToUpdate.keySet()) {
            request.setBodyParam(key, FieldsToUpdate.get(key));
        }

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("updateDocumentFields {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("updateBinderFields {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return successList.size() > 0;
    }
    /**
     * @param docID
     *
     * */
    public Map retrieveDocumentVersions(String docID) {

        Map<String, String> versionInfo = VaultCollections.newMap();

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.GET);
        String apiURL = String.format(
                URL_RETRIEVE_DOCUMENT_VERSIONS,
                apiVersion,
                docID);

        request.appendPath(apiURL);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("retrieveDocumentVersions {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                        JsonArray versionsArray = apiResponse.getArray("versions");

                        for (int i = 0; i < versionsArray.getSize(); i++) {
                            JsonObject versions = versionsArray.getValue(i, JsonValueType.OBJECT);
                            String versionNumber = versions.getValue("number", JsonValueType.STRING);
                            String versionValue= versions.getValue("value", JsonValueType.STRING);
                            versionInfo.put(versionNumber,versionValue);
                        }
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("retrieveDocumentVersions {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return versionInfo;
    }
    /**
     * @param docID
     * @param majorVersion
     * @param minorVersion
     * @param documentFieldsToUpdate
     */
    public boolean updateDocumentBulk(String docID, String majorVersion,
                                        String minorVersion, Map<String, String> documentFieldsToUpdate, String apiConnection) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest(apiConnection);
        request.setMethod(HttpMethod.PUT);
//		request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion);
//		request.appendPath(URL_DOCUMENT_UPDATE,apiVersion,docID,majorVersion,minorVersion);
        String initiateDocumentUpdateUrl = String.format(
                URL_UPDATE_DOCUMENT_VERSION,
                apiVersion,
                docID,
                majorVersion,
                minorVersion);

        request.appendPath(initiateDocumentUpdateUrl);
        for (String key : documentFieldsToUpdate.keySet()) {
            request.setBodyParam(key, documentFieldsToUpdate.get(key));
        }

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onError(response -> {
                    String errorMessage = "HTTP Status Code: " + response.getHttpResponse().getHttpStatusCode();
                    getLogService().error("updateDocumentFields {}", errorMessage);
                    getErrorList().add(errorMessage);
                })
                .onSuccess(response -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(response.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSESTATUS_SUCCESS)) {
                        successList.add(true);
                    } else {
                        JsonArray errors = apiResponse.getErrors();
                        if (errors != null) {
                            for (int i = 0; i < errors.getSize(); i++) {
                                JsonObject error = errors.getValue(i, JsonValueType.OBJECT);
                                String errorType = error.getValue(APIFIELD_ERROR_TYPE, JsonValueType.STRING);
                                String errorMessage = error.getValue(APIFIELD_ERROR_MESSAGE, JsonValueType.STRING);
                                getLogService().error("updateDocumentFields {}", errorType + " - " + errorMessage);
                                getErrorList().add(errorType + " - " + errorMessage);
                            }
                        }
                    }

                })
                .execute();

        return successList.size() > 0;
    }
}