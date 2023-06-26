package com.veeva.vault.custom.util;

import java.util.List;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;

/**
 * Author: Bryan Chan 
 * Date: 24 April 2018
 * Description:  
 * A list of utility methods for supplementing the 
 * Vault SDK Record Service
 * 
 */
@UserDefinedClassInfo
public class RecordServiceUtil {
	private static final int BATCH_SIZE = 500;
	
	/**
	 * Performs a batch update based on a list of records.
	 * This method will break down the list by the batch
	 * size
	 * 
	 * @param recordList list of record to be executed
	 */
	public static void updateRecordList(List<Record> recordList, String objectName) {
		final String methodName = RecordServiceUtil.class.toString() + ".updateRecordList()";
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		Log.entry(methodName + ", Record Size: " + recordList.size());
		
		if (recordList.size() > 0) {
			List<List<Record>> batchRecords = partition(recordList);
			
			for (List<Record> batchRecord : batchRecords) {
				Log.debug("Saving batch START");
				recordService.batchSaveRecords(batchRecord)
					.onSuccesses(batchOperationSuccess -> {
						batchOperationSuccess.stream().forEach(success -> {
							Log.debug("Successfully created/updated record with id: " + success.getRecordId() + " for object [" + objectName + "]");	
							});
					})		
					.onErrors(batchOperationErrors -> {
							batchOperationErrors.stream().forEach(error -> {
								String errMsg = error.getError().getMessage();
								int errPosition = error.getInputPosition();
								String id = recordList.get(errPosition).getValue("id", ValueType.STRING);
								
								if (id != null) {
									final String message = "Record ID: " + id + ", Unable to update record because of the following error: " + errMsg;
									Log.debug(message);
									throw new RollbackException("OPERATION_NOT_ALLOWED", message);
								} else {
									final String message = "Record ID: " + id + ", Unable to create record because of the following error: " + errMsg;
									Log.debug(message);
									throw new RollbackException("OPERATION_NOT_ALLOWED", message);
								}
							});
					}).execute();
				Log.debug("Saving batch END");
			}
		}
		Log.exit(methodName + ", Record Size: " + recordList.size());
	}

	/**
	 * Performs a batch update based on a list of records.
	 * This method will break down the list by the batch
	 * size
	 * 
	 * @param recordList list of record to be executed
	 */
	public static void deleteRecordList(List<Record> recordList, String objectName) {
		final String methodName = RecordServiceUtil.class.toString() + ".deleteRecordList()";
		RecordService recordService = ServiceLocator.locate(RecordService.class);
		Log.entry(methodName + ", Record Size: " + recordList.size());
		
		if (recordList.size() > 0) {
			List<List<Record>> batchRecords = partition(recordList);
			
			for (List<Record> batchRecord : batchRecords) {
				recordService.batchDeleteRecords(batchRecord)
				.onSuccesses(batchOperationSuccess -> {
					batchOperationSuccess.stream().forEach(success -> {
							Log.debug("Successfully deleted record with id: " + success.getRecordId() + " for object [" + objectName + "]");	
						});
				})		
				.onErrors(batchOperationErrors -> {
						batchOperationErrors.stream().forEach(error -> {
							String errMsg = error.getError().getMessage();
							int errPosition = error.getInputPosition();
							String id = recordList.get(errPosition).getValue("id", ValueType.STRING);
							final String message = "Record ID: " + id + " with position " + errPosition + ", Unable to delete record because of the following error: " + errMsg;
							Log.debug(message);
							throw new RollbackException("OPERATION_NOT_ALLOWED", message);
						});
				}).execute();
			}
		}
		Log.exit(methodName + ", Record Size: " + recordList.size());
	}
	
	/**
	 * partitions a list into a multiple list 
	 * based on the batch size
	 * @param recordList the list of records to be partitioned
	 * @return multiple list of records based on the batch
	 */
	@SuppressWarnings("unchecked")
	private static List<List<Record>> partition(List<Record> recordList) {
		List<List<Record>> partitions = VaultCollections.newList();
		
		for (int i=0; i<recordList.size(); i += BATCH_SIZE) {
	        partitions.add(recordList.subList(i, min(i + BATCH_SIZE, recordList.size())));
	    }
		
		return partitions;
	}
	
	/**
	 * A copy of the Math.min library. 
	 * Return the minimum value between a and b
	 * @param a integer 1
	 * @param b integer 2
	 * @return the integer that has the lower value
	 */
	private static int min(int a, int b) {
		return (a > b) ?  b : a;
	}
}