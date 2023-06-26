package com.veeva.vault.custom.model;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

@UserDefinedClassInfo
public class SASectionRule {
	private String matchingRules;
	private String condition;
	private String saGrouping;
	
	public String getMatchingRules() {
		return matchingRules;
	}
	public String getCondition() {
		return condition;
	}
	public String getSaGrouping() {
		return saGrouping;
	}
	public void setMatchingRules(String matchingRules) {
		this.matchingRules = matchingRules;
	}
	public void setCondition(String condition) {
		this.condition = condition;
	}
	public void setSaGrouping(String saGrouping) {
		this.saGrouping = saGrouping;
	}
	
	
}