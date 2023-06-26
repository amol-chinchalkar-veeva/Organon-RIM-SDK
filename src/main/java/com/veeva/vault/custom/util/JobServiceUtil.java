package com.veeva.vault.custom.util;

import java.util.List;

import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;

/**
 * Author: Bryan Chan 
 * Date: 24 April 2018
 * Description:  
 * A list of utility methods for supplementing the 
 * Vault SDK Job Service
 * 
 */

@UserDefinedClassInfo
public class JobServiceUtil {
	
	/**
	 * Initiates a record workflow action based on the record and action name
	 * 
	 * @param record
	 * @param actionName
	 * 
	 */
	public static void initiateBulkWorkflowAction(List<Record> records, String actionName) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.info("[ENTRY] " + JobServiceUtil.class.toString() + ".initiateBulkWorkflowAction(), Action: " + actionName);
		JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters("record_user_action__v");
        jobParameters.setValue("user_action_name", actionName);
        jobParameters.setValue("records", records);
        jobService.run(jobParameters);
        log.info("[EXIT] " + JobServiceUtil.class.toString() + ".initiateBulkWorkflowAction(), Action: " + actionName);
	}
}