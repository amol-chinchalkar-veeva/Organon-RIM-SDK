package com.veeva.vault.custom.services;

import com.veeva.vault.custom.classes.api.VpsAPIResponse;
import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.custom.model.UserRoleTemplateMapping;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.ReadRecordsResponse;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;

import java.util.List;
import java.util.Map;

@UserDefinedServiceInfo
public class VpsUserRoleTemplateGroupServiceImpl implements VpsUserRoleTemplateGroupService {

    private static final String ERROR_URTM = "Invalid setup data. No User Role Template Mappings exist.";

    @Override
    public void initTemplateGroup(TemplateGroup templateGroup) {
        String RESPONSE_STATUS_SUCCESS = "SUCCESS";
        String LOCAL_CONNECTION = "local_http_callout_connection";
        String URL_QUERY = "/api/v21.2/query";

        List<String> urtIds = VaultCollections.newList();

        if (templateGroup == null) return;

        templateGroup.setUrtm(VaultCollections.newList());
        templateGroup.setTemplates(VaultCollections.newList());

        // object name - need to get the label from the picklist
        PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
        Picklist picklistURSObject = picklistService.getPicklist("user_role_setup_object__c");
        templateGroup.setObjectName(picklistURSObject.getPicklistValue(templateGroup.getListURS().get(0)).getLabel());
        templateGroup.setObjectNamePL(templateGroup.getListURS().get(0));

        // urtm - Retrieve the user role template mapping setup data
        String query = "select template_field__c,user_role_setup_field__c,is_picklist__c from user_role_template_mapping__c where user_role_setup_object__c = '" + templateGroup.getObjectNamePL() + "' and status__v = 'active__v'";

        HttpService httpService = ServiceLocator.locate(HttpService.class);
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

        if (templateGroup.getUrtm() == null || templateGroup.getUrtm().size() < 1) {
            RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", ERROR_URTM);
            throw rollbackException;
        }

        // templates - Retrieve all the template records and field values for the template group
        // this uses readRecords to generically retrieve a list of records to be referenced later
        query = "select id from user_role_template__c where template_group__c='" + templateGroup.getTemplateGroupId() + "'";

        HttpRequest urTemplateRequest = httpService.newHttpRequest(LOCAL_CONNECTION)
                .setMethod(HttpMethod.POST)
                .setBodyParam("q", query)
                .appendPath(URL_QUERY);

        httpService.send(urTemplateRequest, HttpResponseBodyValueType.STRING)
                .onSuccess(httpResponse -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(httpResponse.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSE_STATUS_SUCCESS)) {
                        JsonArray jsonArray = apiResponse.getArray("data");
                        if (jsonArray != null) {
                            for (int i = 0; i < jsonArray.getSize(); i++) {
                                JsonObject dataItem = jsonArray.getValue(i, JsonValueType.OBJECT);
                                String id = dataItem.getValue("id", JsonValueType.STRING);
                                urtIds.add(id);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();

        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> listRecord = VaultCollections.newList();

        for (String id : urtIds) {
            listRecord.add(recordService.newRecordWithId("user_role_template__c", id));

            if (listRecord.size() == 500) {
                ReadRecordsResponse rrr = recordService.readRecords(listRecord);
                Map<String,Record> m = rrr.getRecords();
                for (String s : m.keySet()) {
                    // Check for active status (via read records instead of the query from above to ensure proper processing from template object)
                    Record r = m.get(s);
                    List<String> listStatus = r.getValue("status__v", ValueType.PICKLIST_VALUES);

                    if (listStatus.get(0).equals("active__v"))
                        templateGroup.getTemplates().add(r);
                }

                listRecord = VaultCollections.newList();
            }
        }

        if (listRecord.size() > 0) {
            ReadRecordsResponse rrr = recordService.readRecords(listRecord);
            Map<String, Record> m = rrr.getRecords();
            for (String s : m.keySet()) {
                // Check for active status (via read records instead of the query from above to ensure proper processing from template object)
                Record r = m.get(s);
                List<String> listStatus = r.getValue("status__v", ValueType.PICKLIST_VALUES);

                if (listStatus.get(0).equals("active__v"))
                    templateGroup.getTemplates().add(r);
            }
        }
    }

    /*public void initTemplateGroup(TemplateGroup templateGroup) {
        if (templateGroup == null) return;

        templateGroup.setUrtm(VaultCollections.newList());
        templateGroup.setTemplates(VaultCollections.newList());

        // object name - need to get the label from the picklist
        PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
        Picklist picklistURSObject = picklistService.getPicklist("user_role_setup_object__c");
        templateGroup.setObjectName(picklistURSObject.getPicklistValue(templateGroup.getListURS().get(0)).getLabel());
        templateGroup.setObjectNamePL(templateGroup.getListURS().get(0));

        // urtm - Retrieve the user role template mapping setup data
        String SINGLE_QUOTE = String.valueOf((char) 39);
        String query = "select template_field__c,user_role_setup_field__c,is_picklist__c from user_role_template_mapping__c where user_role_setup_object__c = " + SINGLE_QUOTE + templateGroup.getObjectNamePL() + SINGLE_QUOTE + " and status__v=" + SINGLE_QUOTE + "active__v" + SINGLE_QUOTE;

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(query);
        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();
            templateGroup.getUrtm().add(new UserRoleTemplateMapping(qr.getValue("template_field__c", ValueType.STRING),qr.getValue("user_role_setup_field__c", ValueType.STRING),qr.getValue("is_picklist__c", ValueType.BOOLEAN)));
        }

        if (templateGroup.getUrtm() == null || templateGroup.getUrtm().size() < 1) {
            RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED", ERROR_URTM);
            throw rollbackException;
        }
        // templates - Retrieve all the template records and field values for the template group
        // this uses readRecords to generically retrieve a list of records to be referenced later
        query = "select id from user_role_template__c where template_group__c=" + SINGLE_QUOTE + templateGroup.getTemplateGroupId() + SINGLE_QUOTE;
        queryResponse = queryService.query(query);
        iterator = queryResponse.streamResults().iterator();

        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> listRecord = VaultCollections.newList();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();
            listRecord.add(recordService.newRecordWithId("user_role_template__c", qr.getValue("id", ValueType.STRING)));
        }

        ReadRecordsResponse rrr = recordService.readRecords(listRecord);
        Map<String,Record> m = rrr.getRecords();
        //templates = VaultCollections.newList();
        for (String s : m.keySet()) {
            // Check for active status (via read records instead of the query from above to ensure proper processing from template object)
            Record r = m.get(s);
            List<String> listStatus = r.getValue("status__v", ValueType.PICKLIST_VALUES);

            if (listStatus.get(0).equals("active__v"))
                templateGroup.getTemplates().add(r);
        }
    }*/

    @Override
    public List<Record> getNewUserRecords(TemplateGroup templateGroup, Record recContext) {
        return ServiceLocator
                .locate(VpsUserRoleTemplateProvisionService.class)
                .getNewUserRecords(recContext, templateGroup.getUrtm(), templateGroup.getTemplates(), templateGroup.getObjectName(), templateGroup.getUserAPIName());
    }

    @Override
    public List<Record> getExistingURSRecords(TemplateGroup templateGroup, String userId, String countryValue, String countryAPIName) {
        String RESPONSE_STATUS_SUCCESS = "SUCCESS";
        String LOCAL_CONNECTION = "local_http_callout_connection";
        String URL_QUERY = "/api/v21.2/query";

        List<Record> listRecord = VaultCollections.newList();
        List<String> templateGroupIds = VaultCollections.newList();
        int count = 0;

        // Query all user role setup records for the current user (will later find the matches). Ignore country field.

        StringBuilder query = new StringBuilder()
                .append("select id from ")
                .append(templateGroup.getObjectName())
                .append(" where ")
                .append(templateGroup.getUserAPIName())
                .append(" = '")
                .append(userId)
                .append("'");

        /*StringBuilder query = new StringBuilder()
                .append("select id from ")
                .append(templateGroup.getObjectName())
                .append(" where ")
                .append(templateGroup.getUserAPIName())
                .append(" = '")
                .append(userId)
                .append("' ")
                .append("AND user__vr.status__v = 'active__v'");*/

        Log.debug("getExistingURSRecords Query:" + query);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        HttpRequest request = httpService.newHttpRequest(LOCAL_CONNECTION)
                .setMethod(HttpMethod.POST)
                .setBodyParam("q", query.toString())
                .appendPath(URL_QUERY);

        httpService.send(request, HttpResponseBodyValueType.STRING)
                .onSuccess(httpResponse -> {
                    VpsAPIResponse apiResponse = new VpsAPIResponse(httpResponse.getResponseBody());
                    if (apiResponse.getResponseStatus().equals(RESPONSE_STATUS_SUCCESS)) {
                        JsonArray jsonArray = apiResponse.getArray("data");
                        if (jsonArray != null) {
                            /*
                             * Loop through the user role setup records, find if there is an exact match on all sharing fields in the record
                             * 1. While loop of all user role setup records
                             *  2. For loop for the fields on the template to set the data
                             */
                            for (int i = 0; i < jsonArray.getSize(); i++) {
                                JsonObject dataItem = jsonArray.getValue(i, JsonValueType.OBJECT);
                                String id = dataItem.getValue("id", JsonValueType.STRING);
                                templateGroupIds.add(id);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();

        RecordService recordService = ServiceLocator.locate(RecordService.class);

        // the current template and user role setup record are exact match, lets delete it
        for (String id : templateGroupIds) {
            count++;
            listRecord.add(recordService.newRecordWithId(templateGroup.getObjectName(), id));
            Log.debug("id :" + templateGroup.getObjectName()+">id>"+ id);
            break;
        }

        Log.debug("Total Existing URS Record:" + count);
        return listRecord;
    }

    /*public List<Record> getExistingURSRecords(TemplateGroup templateGroup, String userId, String countryValue, String countryAPIName) {
        String SINGLE_QUOTE = String.valueOf((char) 39);
        List<Record> listRecord = VaultCollections.newList();

        Map<String,UserRoleTemplateMapping> urtmf = getURTMByURSField(templateGroup);
        // Query all user role setup records for the current user (will later find the matches). Ignore country field.

        String query = "select id,";
        for (String s : urtmf.keySet())
            query += s + ",";

        // New requirement for countries at template level, ensure country is in the query
        if (countryAPIName != null && !countryAPIName.equals("") && !query.contains(countryAPIName + ","))
            query += countryAPIName + ",";

        query = query.substring(0, query.length()-1);
        query += " from " + templateGroup.getObjectName() + " where " + templateGroup.getUserAPIName() + "=" + SINGLE_QUOTE + userId + SINGLE_QUOTE;

        LogService debug = ServiceLocator.locate(LogService.class);
        debug.debug(query);
        debug.debug("countryValue " + countryValue);
        debug.debug("countryAPIName " + countryAPIName);

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(query);
        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

        RecordService recordService = ServiceLocator.locate(RecordService.class);

        int count = 0;
        while (iterator.hasNext()) {
            QueryResult queryResult = iterator.next();
            String userRole = queryResult.getValue("role__v", ValueType.STRING);

            boolean bDelete = true;
            for (Record recTemplate : templateGroup.getTemplates()) {
                String templateRole = recTemplate.getValue("role__c", ValueType.STRING);
                if (userRole.equals(templateRole)) {
                    // 2.9.1 Move to UDS to unserve memories
                    bDelete = ServiceLocator
                            .locate(VpsUserRoleTemplateProvisionService.class)
                            .isDelete(urtmf, queryResult, recTemplate, countryAPIName, countryValue);
                }
            }
            if (bDelete) {
                // the current template and user role setup record are exact match, lets delete it
                listRecord.add(recordService.newRecordWithId(templateGroup.getObjectName(), queryResult.getValue("id", ValueType.STRING)));
                break; // break from the template loop, theres already a match. no need to look at other templates
            }
            count++;
        }
        Log.debug("Total Existing URS Record:" + count);
        return listRecord;
    }*/

    @Override
    public Map<String, UserRoleTemplateMapping> getURTMByURSField(TemplateGroup templateGroup) {
        Map<String, UserRoleTemplateMapping> m = VaultCollections.newMap();
        for (UserRoleTemplateMapping u : templateGroup.getUrtm())
            m.put(u.getUser_role_setup_field(),u);

        return m;
    }
}