package com.veeva.vault.custom.util;

import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * Author: Bryan Chan 
 * Date: 24 April 2018 
 * 
 * Description: Wrapper class for the actual LogService
 * 
 */

@UserDefinedClassInfo
public class Log {
	
	public static void debug(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.debug(message);
	}
	
	public static void info(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.info(message);
	}

	public static void error(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.error(message);
	}
	
	public static boolean isDebugEnabled(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		return log.isDebugEnabled();
	}
	
	public static void entry(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.info("[ENTRY] " + message);
	}
	
	public static void exit(String message) {
		LogService log = ServiceLocator.locate(LogService.class);
		log.info("[EXIT] " + message);
	}
}