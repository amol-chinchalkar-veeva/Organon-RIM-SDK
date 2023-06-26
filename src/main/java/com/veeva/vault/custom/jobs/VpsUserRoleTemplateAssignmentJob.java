package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.util.api.VpsAPIResponse;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.util.List;

@JobInfo(adminConfigurable = true)
public class VpsUserRoleTemplateAssignmentJob implements Job {
    public static final String JOB_NAME = "vps_user_role_template_assignment_job__c";

    @Override
    public JobInputSupplier init(JobInitContext jobInitContext) {
        String query = jobInitContext.getJobParameter("query", JobParamValueType.STRING);

        List<JobItem> jobItems = VaultCollections.newList();

        String RESPONSE_STATUS_SUCCESS = "SUCCESS";
        String LOCAL_CONNECTION = "local_http_callout_connection";
        String URL_QUERY = "/api/v21.2/query";

        List<String> ids = VaultCollections.newList();

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
                                String id = dataItem.getValue("id", JsonValueType.STRING);
                                ids.add(id);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {

                })
                .execute();

        JobLogger jobLogger = jobInitContext.getJobLogger();
        jobLogger.log("Total Existing URS Record:" + ids.size());

        for (String id : ids) {
            JobItem jobItem = jobInitContext.newJobItem();
            jobItem.setValue("id", id);
            jobItems.add(jobItem);
        }

        return jobInitContext.newJobInput(jobItems);
    }

    @Override
    public void process(JobProcessContext jobProcessContext) {
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        JobLogger jobLogger = jobProcessContext.getJobLogger();
        List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();
        List<Record> recordList = VaultCollections.newList();

        for (JobItem jobItem : jobItems) {
            Record record = recordService.newRecordWithId(
                    "user_role_template_assignment__c",
                    jobItem.getValue("id", JobValueType.STRING)
            );

            recordList.add(record);
        }

        jobLogger.log("Number of records being processed: " + recordList.size());

        if (recordList.size() > 0) {
            jobLogger.log("Creating records of size: " + recordList.size());

            recordService.batchSaveRecords(recordList)
                    .onErrors(batchOperationErrors -> {
                        batchOperationErrors.stream().findFirst().ifPresent(error -> {
                            String errMsg = error.getError().getMessage();
                            jobLogger.log(errMsg);
                        });
                    })
                    .execute();
        };
    }

    @Override
    public void completeWithSuccess(JobCompletionContext context) {
        completeJob(context);
    }

    @Override
    public void completeWithError(JobCompletionContext context) {
        completeJob(context);
    }

    public void completeJob(JobCompletionContext context) {
        JobLogger logger = context.getJobLogger();
        JobResult result = context.getJobResult();

        int failedTaskCount = result.getNumberFailedTasks();
        if (failedTaskCount > 0) {
            logger.log("Complete with error: " + result.getNumberFailedTasks() + " tasks failed out of " + result.getNumberTasks());
            List<JobTask> tasks = context.getTasks();
            for (JobTask task : tasks) {
                TaskOutput taskOutput = task.getTaskOutput();
                if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
                    logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
                }
            }
        }
        else {
            logger.log("All tasks completed successfully, total: " + result.getNumberCompletedTasks());
        }
    }
}