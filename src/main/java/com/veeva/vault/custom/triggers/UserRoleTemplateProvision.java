package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.services.VpsUserRoleTemplateProvisionService;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultRuntimeException;
import com.veeva.vault.sdk.api.data.*;
import java.util.List;


/**
 * Object: user_role_template__c
 * Trigger API Name: user_role_template_provision__c
 * Author: Todd Taylor
 * Date: 27 July 2017
 *
 * Reprocess the user role provisioning data by performing an update
 * on the user role template assignment object. Run conditions:
 * 1. Active record
 * 2. Record was just inactivated (trigger.old = active, trigger.new = inactive
 * 
 * UI Changes only (this is for setup data in the browser)
 */
@RecordTriggerInfo(object = "user_role_template__c", events = {RecordEvent.AFTER_UPDATE}, name="user_role_template_provision__c")
public class UserRoleTemplateProvision implements RecordTrigger   {
    public void execute(RecordTriggerContext recordTriggerContext)  {

		VpsUserRoleTemplateProvisionService vpsUserRoleTemplateProvisionService =
				ServiceLocator.locate(VpsUserRoleTemplateProvisionService.class);

		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {			
			try {							
				List<String> listStatus = inputRecord.getNew().getValue("status__v", ValueType.PICKLIST_VALUES);				
				String statusCurrent = listStatus.get(0);	
				
				listStatus = inputRecord.getOld().getValue("status__v", ValueType.PICKLIST_VALUES);
				String statusPrevious = listStatus.get(0);	
				
				if (statusCurrent.equals("active__v") || (statusCurrent.equals("inactive__v") && statusPrevious.equals("active__v")))
					vpsUserRoleTemplateProvisionService.refreshUserProvisioningRecords(inputRecord.getNew().getValue("template_group__c", ValueType.STRING));
			}
			catch(VaultRuntimeException e) {
				 RollbackException rollbackException = new RollbackException("OPERATION_NOT_ALLOWED",
         	            e.getMessage() + ".  If you feel this is an error, please contact your IT Administrator.");
         	    throw rollbackException;
           }
		}    		     	   		
    }
}