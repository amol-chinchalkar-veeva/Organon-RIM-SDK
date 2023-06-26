package com.veeva.vault.custom.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.veeva.vault.custom.model.BDLActivityObjectType;
import com.veeva.vault.custom.model.BDLTaskObjectType;
import com.veeva.vault.custom.model.SDKSetting;
import com.veeva.vault.custom.modules.BDLPopulateMultiAgreementTaskQuery;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;

/**
 * Author: Bryan Chan 
 * Date: 2019-Feb-12
 * Description:  
 * A list of utility methods for supplementing the 
 * Vault SDK Job Service
 * 
 */

@UserDefinedClassInfo
public class BDLUtil {
	
	
	@SuppressWarnings("unchecked")
	public static List<SDKSetting> retrieveAllSDKSettings() {
		final String methodName = BDLPopulateMultiAgreementTaskQuery.class.toString() + ".retrieveAllSDKSettings()";
		Log.entry(methodName);
		List<SDKSetting> sdkSettings = VaultCollections.newList();
		
		final String query = "SELECT id,name__v,object_name__c,object_type__c,object_subtype__c,criteria_vql__c,activity_to_task_mappings__c FROM vps_bdl_rules__c WHERE status__v='active__v'";
		QueryResponse response = QueryServiceUtil.query(query);
		
		response.streamResults().forEach(queryResult -> {
			SDKSetting setting = new SDKSetting();
			setting.setId(queryResult.getValue("id",ValueType.STRING));
			setting.setName(queryResult.getValue("name__v",ValueType.STRING));
			setting.setObjectName(queryResult.getValue("object_name__c",ValueType.STRING));
			setting.setObjectType(queryResult.getValue("object_type__c",ValueType.STRING));
			setting.setObjectSubType(queryResult.getValue("object_subtype__c",ValueType.STRING));
			setting.setCriteriaVQL(queryResult.getValue("criteria_vql__c",ValueType.STRING));
			setting.setMappings(queryResult.getValue("activity_to_task_mappings__c",ValueType.STRING));
			sdkSettings.add(setting);
        });
		
		
		Log.exit(methodName + ", SDK Setting Size:" + sdkSettings.size());
		return sdkSettings;
	}
	
	/**
	 * 
	 */
	public static Map<String, String> retrieveBDLObjectTypeMap(String objectName) {
		final String methodName = BDLUtil.class.toString() + ".retrieveObjectTypeMap()";
		Log.entry(methodName);
		
		BDLActivityObjectType activityObjectType = RequestContext.get().getValue("BDL_ACTIVITY_OBJECT_TYPE", BDLActivityObjectType.class);
		BDLTaskObjectType taskObjectType = RequestContext.get().getValue("BDL_TASK_OBJECT_TYPE", BDLTaskObjectType.class);
	 	// This is the first time a trigger runs, grab all SDK Mapping
	 	if (activityObjectType == null || taskObjectType == null) {
	 		activityObjectType = new BDLActivityObjectType();	
	 		taskObjectType = new BDLTaskObjectType();	
	 		final String query = "SELECT id,object_name__v,api_name__v FROM object_type__v WHERE status__v='active__v' AND object_name__v CONTAINS ('bdl_multi_agreement_activity__c','multi_agreement_tasks__c')";
			QueryResponse queryResponse = QueryServiceUtil.query(query);
			Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
			while (iterator.hasNext()) {
				QueryResult queryResult = iterator.next();
				String id = queryResult.getValue("id", ValueType.STRING);
				String object = queryResult.getValue("object_name__v", ValueType.STRING);
				String apiName = queryResult.getValue("api_name__v", ValueType.STRING);
				if (object.equals("bdl_multi_agreement_activity__c")) {
					activityObjectType.put(id, apiName);
				} else if (object.equals("multi_agreement_tasks__c")) { 
					taskObjectType.put(apiName, id);
				}
				
			}
			
	        RequestContext.get().setValue("BDL_ACTIVITY_OBJECT_TYPE", activityObjectType);
	        RequestContext.get().setValue("BDL_TASK_OBJECT_TYPE", taskObjectType);
	 	} 
	 	
	 	Log.exit(methodName);
	 	if (objectName.equals("bdl_multi_agreement_activity__c")) {
	 		Log.debug("Activity Object Type Map found.  Size: " + activityObjectType.size());
	 		return activityObjectType.getObjectTypes();
	 	} else {
	 		Log.debug("Task Object Type Map found.  Size: " + taskObjectType.size());
	 		return taskObjectType.getObjectTypes();
	 	}
	}
	
	/**
	 * @param sdkSettings
	 * @param applicableRecord
	 * @param activityObjectType
	 * @return
	 */
	public static SDKSetting retrieveSDKSetting(List<SDKSetting> sdkSettings, RecordChange applicableRecord, Map<String, String> activityObjectType) {
		
		String objectName = applicableRecord.getNew().getObjectName();
		String objectTypeId = applicableRecord.getNew().getValue("object_type__v", ValueType.STRING);
		String objectTypeName = activityObjectType.get(objectTypeId);
		
		SDKSetting setting = null;
		for (SDKSetting sdkSetting:sdkSettings) {
			Log.debug(sdkSetting.getObjectName());
			Log.debug(sdkSetting.getObjectType());
			if (objectName.equals(sdkSetting.getObjectName()) && objectTypeName.equals(sdkSetting.getObjectType())) {
				String objectSubType = sdkSetting.getObjectSubType();
				if (objectSubType != null && objectSubType.length() > 0) {
					// Subtype can be any field.  <Field Name>.<Field Value> 
					// risk_management_report_type__c.risk_management_plan__c
					Log.debug("SUBTYPE: " + objectSubType);
					String[] subType = StringUtils.split(objectSubType, "\\.");
					String fieldName = subType[0];
					String fieldValue = subType[1];
					if (applicableRecord.getNew().getValue(fieldName, ValueType.PICKLIST_VALUES).contains(fieldValue)) {
						setting = sdkSetting;
						break;
					}
				} else {
					setting = sdkSetting;	
					break;
				}
			}
		}
		
		if (setting != null) {
			Log.debug("Setting Found: [" + setting.getName() + "]");
		}
		
		return setting;
	}
}