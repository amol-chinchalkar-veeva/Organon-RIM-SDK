package com.veeva.vault.custom.triggers;

import java.util.Iterator;
import java.util.List;

import com.veeva.vault.custom.model.SASectionRule;
import com.veeva.vault.custom.util.Log;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * Object: submissions_archive_content_section__rim 
 * Trigger API Name: sub_archive_section_level_security__c 
 * Author: Bryan Chan 
 * Version: 17.2.3
 * Date: 22 June 2017
 *
 * Set submission_archive_sections_rim__c.sa_grouping__c based on the sa_section_rule__c rules
 *
 */

@RecordTriggerInfo(object = "submissions_archive_content_section__rim", 
	events = { RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE}, 
	order = TriggerOrder.NUMBER_1)
public class SubmissionArchiveSectionLevelSecurity implements RecordTrigger {
	
	private static final String SUBMISSION_ARCHIVE_FIELD_LABEL_FULL_PATH = "content_section__rim"; 
	private static final String SUBMISSION_ARCHIVE_FIELD_LABEL_SA_GROUPING = "sa_grouping__c";
	private static final String REG_EX_WILDCARD_PATTERN = "(.*?)";
	private static final String SA_SECTION_RULE_WILDCARD = "%";
	private static final String SA_SECTION_RULE_FIELD_LABEL_CONDITION = "condition__c";
	private static final String SA_SECTION_RULE_FIELD_LABEL_MATCHING_RULE = "matching_rule__c";
	private static final String SA_SECTION_RULE_CONDITION_CONTAINS = "contains__c";
	private static final String SA_SECTION_RULE_CONDITION_STARTS_WITH = "starts_with__c";
	private static final String SA_SECTION_RULE_CONDITION_ENDS_WITH = "ends_with__c";
	private static final String SA_SECTION_RULE_QUERY = "select condition__c,matching_rule__c,sa_grouping__c from sa_section_rule__c where status__v='active__v' order by order_of_evaluation__c ASC";
	
	/*
	 * Trigger override for the execute method
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.veeva.vault.sdk.api.data.RecordTrigger#execute(com.veeva.vault.sdk.
	 * api.data.RecordTriggerContext)
	 */
	public void execute(RecordTriggerContext recordTriggerContext) {
		final String methodName = SubmissionArchiveSectionLevelSecurity.class.toString() + ".execute()";
    	Log.entry(methodName);
    	
		List<SASectionRule> sectionRules = getSASectionRules();
		 
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
			// Get the full path value
			String newFullPath = (inputRecord.getNew().getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_FULL_PATH, ValueType.STRING) == null) 
					? "" : inputRecord.getNew().getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_FULL_PATH, ValueType.STRING);
			if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_INSERT) {
				updateSAGrouping(sectionRules, inputRecord, newFullPath);
			} else if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {
				// Get the previous full path value and only execute if full path has been modified
				String oldFullPath = (inputRecord.getOld().getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_FULL_PATH, ValueType.STRING) == null) 
						? "" : inputRecord.getOld().getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_FULL_PATH, ValueType.STRING);
				if (!oldFullPath.equals(newFullPath)) {
					updateSAGrouping(sectionRules, inputRecord, newFullPath);
				}
			}
		}
		
		Log.exit(methodName);
	}

	/**
	 * Updates the SA Group value for a Submission Archive Section Objecgt
	 * 
	 * @param queryService the service to execute a VQL query
	 * @param inputRecord the record that that has been triggered
	 * @param fullPath the full path of the Submission Archive Section
	 */
	private void updateSAGrouping(List<SASectionRule> sectionRules, RecordChange inputRecord, String fullPath) {
		final String methodName = SubmissionArchiveSectionLevelSecurity.class.toString() + ".updateSAGrouping()";
    	Log.entry(methodName);
		String saGrouping = parseSASectionRules(sectionRules, fullPath);
		inputRecord.getNew().setValue(SUBMISSION_ARCHIVE_FIELD_LABEL_SA_GROUPING, VaultCollections.asList(saGrouping));
		Log.exit(methodName);
	}

	/**
	 * Queries the SA Section Rule object and find the first matching rule
	 * 
	 * @param queryService the service to execute a VQL query
	 * @param fullPath the record that that has been triggered
	 * @return the SA Grouping value
	 */
	private String parseSASectionRules(List<SASectionRule> sectionRules, String fullPath) {
		final String methodName = SubmissionArchiveSectionLevelSecurity.class.toString() + ".parseSASectionRules()";
    	Log.entry(methodName);
		for (SASectionRule sectionRule:sectionRules) {
			if (!sectionRule.getMatchingRules().isEmpty()) {
				boolean hasWildCards = (sectionRule.getMatchingRules().contains(SA_SECTION_RULE_WILDCARD)) ? true : false;
				switch (sectionRule.getCondition()) {
					case SA_SECTION_RULE_CONDITION_CONTAINS:	
						if (hasWildCards) {
							String matchingRules = sectionRule.getMatchingRules().replace(SA_SECTION_RULE_WILDCARD, REG_EX_WILDCARD_PATTERN);
							if (StringUtils.matches(fullPath,"(.*?)" + matchingRules + "(.*?)")) {
								Log.exit(methodName + ":" + "Matching rules found: [Wild Card, CONTAINS]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						} else {
							if (fullPath.contains(sectionRule.getMatchingRules())) {
								Log.exit(methodName + ":" + "Matching rules found: [No Wild Card, CONTAINS]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						}
						break;
					case SA_SECTION_RULE_CONDITION_STARTS_WITH:
						if (hasWildCards) {
							String matchingRules = sectionRule.getMatchingRules().replace(SA_SECTION_RULE_WILDCARD, REG_EX_WILDCARD_PATTERN);
							if (StringUtils.matches(fullPath, matchingRules + "(.*?)")) {
								Log.exit(methodName + ":" + "Matching rules found: [Wild Card, STARTS WITH]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						} else {
							if (fullPath.startsWith(sectionRule.getMatchingRules())) {
								Log.exit(methodName + ":" + "Matching rules found: [No Wild Card, STARTS WITH]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						}
						break;
					case SA_SECTION_RULE_CONDITION_ENDS_WITH:
						if (hasWildCards) {
							String matchingRules = sectionRule.getMatchingRules().replace(SA_SECTION_RULE_WILDCARD, REG_EX_WILDCARD_PATTERN);
							if (StringUtils.matches(fullPath,"(.*?)" + matchingRules)) {
								Log.exit(methodName + ":" + "Matching rules found: [Wild Card, ENDS WITH]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						} else {
							if (fullPath.endsWith(sectionRule.getMatchingRules())) {
								Log.exit(methodName + ":" + "Matching rules found: [No Wild Card, ENDS WITH]: " + sectionRule.getSaGrouping());
								return sectionRule.getSaGrouping();
							}
						}
						break;
					default:
						break;
				}				
			}
		}
		Log.exit(methodName + ":" + "No matching rules found: Defaults to [default__c]");
		return "default__c";
	}
	
	
	/**
	 * Retrieves all the SA Section Rules from the sa_section_rule__c object
	 * 
	 * @return a list of SA Section Rules
	 */
	@SuppressWarnings("unchecked")
	private List<SASectionRule> getSASectionRules() {
		final String methodName = SubmissionArchiveSectionLevelSecurity.class.toString() + ".getSASectionRules()";
    	Log.entry(methodName);
		List<SASectionRule> sectionRules = VaultCollections.newList();
		
		QueryService queryService = ServiceLocator.locate(QueryService.class);
		QueryResponse queryResponse = queryService.query(SA_SECTION_RULE_QUERY);
		Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
		while (iterator.hasNext()) {
			QueryResult queryResult = iterator.next();
			String matchingRules = (queryResult.getValue(SA_SECTION_RULE_FIELD_LABEL_MATCHING_RULE, ValueType.STRING) == null) 
					? "" : queryResult.getValue(SA_SECTION_RULE_FIELD_LABEL_MATCHING_RULE, ValueType.STRING);
			String condition = (queryResult.getValue(SA_SECTION_RULE_FIELD_LABEL_CONDITION, ValueType.PICKLIST_VALUES) == null) 
					? "" : (String) queryResult.getValue(SA_SECTION_RULE_FIELD_LABEL_CONDITION, ValueType.PICKLIST_VALUES).get(0);
			String saGrouping = (queryResult.getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_SA_GROUPING, ValueType.PICKLIST_VALUES) == null) 
					? "" : (String) queryResult.getValue(SUBMISSION_ARCHIVE_FIELD_LABEL_SA_GROUPING, ValueType.PICKLIST_VALUES).get(0);
			
			SASectionRule sectionRule = new SASectionRule();
			sectionRule.setCondition(condition);
			sectionRule.setMatchingRules(matchingRules);
			sectionRule.setSaGrouping(saGrouping);
			sectionRules.add(sectionRule);		
		}
		
		return sectionRules;
	}
}