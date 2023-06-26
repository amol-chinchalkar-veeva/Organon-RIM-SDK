package com.veeva.vault.custom.util;

import java.util.Set;

import com.veeva.vault.custom.model.NestedTriggerContext;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.RecordChange;
import com.veeva.vault.sdk.api.data.RecordEvent;

/**
 * Author: Bryan Chan 
 * Date: 24 April 2018
 * Description:  
 * A list of helper utility methods for triggers
 * 
 */
@UserDefinedClassInfo
public class TriggerUtil {

	/**
	 * Compares two objects depending upon their instance to determine if they
	 * are the different. null & null = false not null & not null = true if
	 * value is the same, false otherwise null & not null = true not null & null
	 * = true
	 */	 
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean fieldChanged(RecordChange rc, String fld, ValueType vt,RecordEvent re) {
		Object old = "";
		if(re.equals(RecordEvent.AFTER_UPDATE) || re.equals(RecordEvent.BEFORE_UPDATE))old = rc.getOld().getValue(fld, vt);
		Object newObj = rc.getNew().getValue(fld, vt);
		boolean ret;
		if(newObj == null && old != null) {
			ret = true;
		}else if(newObj != null && old == null) {
			ret = true;
		}else if(newObj == null && old == null) {
			ret = false;
		}else {
			ret = !newObj.equals(old);
		}
		return ret;
	}
	
	/**
	 * Parses out a string and collect a unique set of tokens for that string
	 * 
	 * @param text
	 *            - the string with unresolved tokens in it
	 * @return a unique set of tokens for a string
	 */
	@SuppressWarnings("unchecked")
	public static Set<String> retrieveTokensFromText(String text) {
		final char DOLLAR_SIGN = (char) 36;
		final char OPEN_BRACKET = (char) 123;
		final char CLOSE_BRACKET = (char) 125;
		final char ZERO = (char) 48;
		Set<String> tokens = VaultCollections.newSet();

		// Keep track of the previous character and the current character. ZERO
		// is just a dummy initializer.
		char previous = ZERO;
		char current = ZERO;
		StringBuilder token = new StringBuilder();
		boolean isToken = false;
		// Java.String.Util.RegEx is not available. Have to loop through the
		// string char by char
		for (int i = 0; i < text.length(); i++) {
			current = text.charAt(i);

			// Find the end of the token pattern
			if (isToken && current == CLOSE_BRACKET) {
				isToken = false;
				if (token.length() > 0) {
					tokens.add(token.toString());
				}
				token.setLength(0);
			}

			// the actual value inside the token
			if (isToken) {
				token.append(current);
			}

			// Find the start of the token pattern
			if (previous == DOLLAR_SIGN && current == OPEN_BRACKET) {
				isToken = true;
			}

			previous = current;
		}

		return tokens;
	}
	
	/**
	 * Retrieves the nested trigger list
	 * 
	 * @return a nested trigger context
	 */
	public static NestedTriggerContext getNestedTriggerContext() {
		final String methodName = TriggerUtil.class.toString() + ".getNestedTriggerContext()";
		Log.entry(methodName);
		NestedTriggerContext nestedTriggerList = RequestContext.get().getValue("NESTED_TRIGGERS", NestedTriggerContext.class);
		if (nestedTriggerList == null) {
			nestedTriggerList = new NestedTriggerContext();
			RequestContext.get().setValue("NESTED_TRIGGERS", nestedTriggerList);
		}
		Log.exit(methodName);
		return nestedTriggerList;
	}

	
	/**
	 * Add your trigger to the nested trigger  list
	 * 
	 * @param triggerName the name of the trigger
	 */
	public static void addNestedTriggerContext(String triggerName) {
		final String methodName = TriggerUtil.class.toString() + ".addNestedTriggerContext()";
		Log.entry(methodName);
		NestedTriggerContext nestedTriggerList = RequestContext.get().getValue("NESTED_TRIGGERS", NestedTriggerContext.class);
		if (nestedTriggerList == null) {
			nestedTriggerList = new NestedTriggerContext();
			nestedTriggerList.add(triggerName);
			RequestContext.get().setValue("NESTED_TRIGGERS", nestedTriggerList);
		} else {
			nestedTriggerList.add(triggerName);
		}
		Log.exit(methodName);
	}
}