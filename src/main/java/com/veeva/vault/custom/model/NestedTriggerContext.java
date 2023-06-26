package com.veeva.vault.custom.model;

import java.util.List;

import com.veeva.vault.sdk.api.core.RequestContextValue;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;

@UserDefinedClassInfo
public class NestedTriggerContext implements RequestContextValue {
	
	@SuppressWarnings("unchecked")
	private List<String> trigger = VaultCollections.newList();

	public List<String> getTriggerList() {
		return trigger;
	}

	public void add(String triggerName) {
		this.trigger.add(triggerName);
	}

	
}