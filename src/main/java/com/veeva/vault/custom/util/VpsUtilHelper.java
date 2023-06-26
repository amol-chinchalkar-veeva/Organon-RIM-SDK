/*
 * --------------------------------------------------------------------
 * UDC:         VpsUtilHelper
 * Author:      achinchalkar @ Veeva
 * Date:        2019-07-25
 * --------------------------------------------------------------------
 * Description: General Utils
 * --------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 * This code is based on pre-existing content developed and
 * owned by Veeva Systems Inc. and may only be used in connection
 * with the deliverable with which it was provided to Customer.
 * --------------------------------------------------------------------
 */
package com.veeva.vault.custom.util;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.picklist.Picklist;
import com.veeva.vault.sdk.api.picklist.PicklistService;

import java.util.List;
import java.util.Set;

import static java.lang.Math.abs;

@UserDefinedClassInfo
public class VpsUtilHelper {

	/**
	 * Returns the first value in the picklist. if no items exists, returns null
	 *
	 * @param values list of values
	 * @return string value of the first item in the list
	 */
	public static String getFirstPickListValue(List<String> values) {
		if ((values != null) && (values.size() > 0)) {
			return values.get(0);
		}
		else {
			return null;
		}
	}
	/**
	 * Returns the  picklist label as Set. if no items exists, returns null
	 *
	 * @param inputRecord Record
	 * @param picklistFieldName picklist name
	 * @return picklistlabels as Set
	 */
	public static Set<String> getPicklistLabels(Record inputRecord, String picklistFieldName) {
		PicklistService picklistService = ServiceLocator.locate(PicklistService.class);
		Picklist picklist = picklistService.getPicklist(picklistFieldName);
		List<String> picklistValues = inputRecord.getValue(picklistFieldName, ValueType.PICKLIST_VALUES);

		Set<String> picklistLabels = VaultCollections.newSet();
		if (picklistValues != null && !picklistValues.isEmpty()) {

			for (String picklistValue : picklistValues) {
				//Get picklist value label
				String picklistValueLabel =
						picklist.getPicklistValue(picklistValue).getLabel();
				picklistLabels.add(picklistValueLabel);
			}
		}
		return picklistLabels;
	}
	/**
	 * Generates a hash value of a given string
	 *
	 * @param value string value to hash
	 * @return hash value padded to 12 characters with +/-; null if input is null
	 */
	public static String getHash(String value) {
		if (value != null) {
			StringBuilder result = new StringBuilder();
			int hash = value.hashCode();

			if (hash < 0) {
				result.append("-");
			}
			else {
				result.append("+");
			}
			result.append(String.format("%11s", abs(hash)).replace(' ', '0'));
			return result.toString();
		}
		else {
			return null;
		}
	}

	/**
	 * Reverses a given string value
	 *
	 * @param value string value to reverse
	 * @return reversed string; null if input is null
	 */
	public static String getReverseString(String value) {
		if (value != null) {
			return new StringBuilder(value).reverse().toString();
		}
		else {
			return null;
		}
	}

	/**
	 * Generates a unique key by concatenating the hash value of the input
	 * and the hash value of the input in reverse, seperated by pipe
	 *
	 * @param value string value to transform into unique key
	 * @return 25 character unique key; null if input is null
	 */
	public static String getUniqueKey(String value) {
		if (value != null) {
			return getHash(value) + "|" + getHash(getReverseString(value));
		}
		else {
			return null;
		}
	}

	/**
	 * Transforms a list into string
	 *
	 * @param valueList list to transform
	 * @param delimiter delimiter between values
	 * @param addQuotes adds single quotes around value
	 * @return string of values
	 */
	public static String listToString(List<String> valueList, String delimiter, Boolean addQuotes) {
		LogService logService = ServiceLocator.locate(LogService.class);
		logService.info("VpsUtilHelper.listToString; delimiter = {}; addQuotes = {}", delimiter, addQuotes);

		StringBuilder result = new StringBuilder();
		if (valueList != null) {
			int i = 0;
			for (String value : valueList) {
				if (i > 0) {
					result.append(delimiter);
				}
				if (addQuotes) {
					result.append("'");
				}
				result.append(value);
				if (addQuotes) {
					result.append("'");
				}
				i++;
			}
		}
		return result.toString();
	}

	/**
	 * Transforms a set into string
	 *
	 * @param valueSet set to transform
	 * @param delimiter delimiter between values
	 * @param addQuotes adds single quotes around value
	 * @return string of values
	 */
	public static String setToString(Set<String> valueSet, String delimiter, Boolean addQuotes) {
		LogService logService = ServiceLocator.locate(LogService.class);
		logService.info("VpsUtilHelper.setToString; delimiter = {}; addQuotes = {}", delimiter, addQuotes);

		StringBuilder result = new StringBuilder();
		if (valueSet != null) {
			int i = 0;
			for (String value : valueSet) {
				if (i > 0) {
					result.append(delimiter);
				}
				if (addQuotes) {
					result.append("'");
				}
				result.append(value);
				if (addQuotes) {
					result.append("'");
				}
				i++;
			}
		}
		return result.toString();
	}
}