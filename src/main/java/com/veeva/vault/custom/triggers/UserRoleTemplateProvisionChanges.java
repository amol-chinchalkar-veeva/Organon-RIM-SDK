/*
 * --------------------------------------------------------------------
 * Trigger: UserRoleTemplateProvisionChanges
 * Author: Todd Taylor
 * Created Date:        2017-07-27
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:
 * Delete existing user role setup records if the template data changes or the record is inactivated.
 * 1. Query all users for the current template group
 * 2. Query the corresponding user role setup records
 * 3. Delete the user role setup records if there is a match to the previous data
 *
 * UI Changes only (this is for setup data in the browser)
 *---------------------------------------------------------------------
 * Revision:
 * 2020-07-27: Initial Release
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved inner class to it's own UDC.
 *    Moved Delete Query to UDS to solve memory issue. See VpsUserRoleTemplateProvisionService
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.classes.api.VpsAPIResponse;
import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.custom.services.VpsUserRoleProvisioningSetupDataService;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.Set;

@RecordTriggerInfo(object = "user_role_template__c", events = {RecordEvent.BEFORE_UPDATE}, order = TriggerOrder.NUMBER_5, name="user_role_template_provision_changes__c")
public class UserRoleTemplateProvisionChanges implements RecordTrigger  {

	public void execute(RecordTriggerContext recordTriggerContext)  {
		LogService debug = ServiceLocator.locate(LogService.class);
		debug.logResourceUsage("UserRoleTemplateProvisionChanges");

		VpsUserRoleProvisioningSetupDataService vpsUserRoleProvisioningSetupDataService =
				ServiceLocator.locate(VpsUserRoleProvisioningSetupDataService.class);

		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

			try {
				String templateGroupId = inputRecord.getNew().getValue("template_group__c", ValueType.STRING);
				Set<String> users = vpsUserRoleProvisioningSetupDataService.getCurrentUsers(templateGroupId);

				if (users != null && users.size() > 0) {
					TemplateGroup templateGroup = new TemplateGroup(templateGroupId);
					vpsUserRoleProvisioningSetupDataService.initTemplateGroup(templateGroup);
					vpsUserRoleProvisioningSetupDataService.deleteExistingRecords(templateGroup, users, inputRecord.getOld());
				}
			}
			catch(VaultRuntimeException e) {
				inputRecord.setError("OPERATION_NOT_ALLOWED",
						e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
			}
		}
	}
}