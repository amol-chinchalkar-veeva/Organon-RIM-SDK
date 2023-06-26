package com.veeva.vault.custom.util;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * Author: Bryan Chan 
 * Date: 24 April 2018
 * Description:  
 * A list of helper utility methods for querying
 * 
 */
@UserDefinedClassInfo
public class QueryServiceUtil {

	public static QueryResponse query(String vqlQuery) {
		QueryService queryService = ServiceLocator.locate(QueryService.class);
		Log.debug("Executing VQL: " + vqlQuery);
		return queryService.query(vqlQuery);
	}
}