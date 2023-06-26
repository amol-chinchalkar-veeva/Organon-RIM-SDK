package com.veeva.vault.custom.model;

import java.util.Map;

import com.veeva.vault.sdk.api.core.RequestContextValue;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;

@UserDefinedClassInfo
public class BDLActivityObjectType implements RequestContextValue {
	
	@SuppressWarnings("unchecked")
	private Map<String,String> objectTypes = VaultCollections.newMap();

	public Map<String, String> getObjectTypes() {
		return objectTypes;
	}

	public void setObjectTypes(Map<String, String> objectTypeMap) {
		this.objectTypes = objectTypeMap;
	}

	public String get(String objectTypeId) {
		return objectTypes.get(objectTypeId);
	}
	
	public void put(String objectTypeId, String objectTypeName) {
		this.objectTypes.put(objectTypeId, objectTypeName);
	}

	public int size() {
		return objectTypes.size();
	}
}