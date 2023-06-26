package com.veeva.vault.custom.model;

import java.util.Map;

import com.veeva.vault.sdk.api.core.RequestContextValue;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;

@UserDefinedClassInfo
public class SDKSetting implements RequestContextValue {
	
	private String id;
	private String name;
	private String objectName;
	private String objectType;
	private String objectSubType;
	private String criteriaVQL;
	private String mappings;
	
	public String getId() {
		return id;
	}
	public String getName() {
		return name;
	}
	public String getObjectName() {
		return objectName;
	}
	public String getObjectType() {
		return objectType;
	}
	public String getObjectSubType() {
		return objectSubType;
	}
	public String getCriteriaVQL() {
		return criteriaVQL;
	}
	public void setId(String id) {
		this.id = id;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}
	public void setObjectType(String objectType) {
		this.objectType = objectType;
	}
	public void setObjectSubType(String objectSubType) {
		this.objectSubType = objectSubType;
	}
	public void setCriteriaVQL(String criteriaVQL) {
		this.criteriaVQL = criteriaVQL;
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, Map<String,String>> getMappings() {
		Map<String, Map<String,String>> taskMappings = VaultCollections.newMap();
		String[] fieldMappings = StringUtils.split(mappings, "\\|");
		for (String fields:fieldMappings) {
			String[] splitFields = StringUtils.split(fields, ":");
			if (splitFields.length == 2) {
				Map<String,String> fieldMapping = VaultCollections.newMap();
				String[] relationshipName = StringUtils.split(splitFields[0],"\\.");
				
				if (relationshipName.length == 2) {
					fieldMapping.put(relationshipName[1], splitFields[1]);
					if (taskMappings.containsKey(relationshipName[0])) {
						taskMappings.get(relationshipName[0]).putAll(fieldMapping);
					} else {
						taskMappings.put(relationshipName[0], fieldMapping);
					}
				} else if (relationshipName.length == 1) {
					fieldMapping.put(splitFields[0], splitFields[1]);
					taskMappings.put("bdl_multi_agreement_activity__c", fieldMapping);
				}
			}
		}
		return taskMappings;
	}
	public void setMappings(String mappings) {
		this.mappings = mappings;
	}

}