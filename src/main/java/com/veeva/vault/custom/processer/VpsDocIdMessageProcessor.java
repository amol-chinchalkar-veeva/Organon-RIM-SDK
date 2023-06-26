/*
 * --------------------------------------------------------------------
 * MessageProcessor:	VpsDocIdMessageProcesser
 * Author:				achinchalkar @ Veeva
 * Date:				2020-05-26
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 *		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.processer;


import com.veeva.vault.custom.util.VpsVQLHelper;
import com.veeva.vault.custom.util.api.VpsAPIClient;
import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.queue.*;

import java.math.BigDecimal;
import java.util.Map;

@MessageProcessorInfo()
public class VpsDocIdMessageProcessor implements MessageProcessor {

    private static final String DOCFIELD_EXPORT_FILENAME = "export_filename__v";
    private static final String DOCFIELD_BASE30_DOCUMENT_ID = "document_id__c";
    private static final String DOCFIELD_MAJOR_VERSION_NUMBER = "major_version_number__v";
    private static final String DOCFIELD_MINOR_VERSION_NUMBER = "minor_version_number__v";
    private static final String DOCFIELD_ID = "id";
    private static final String DOCFIELD_BINDER = "binder__v";


    public void execute(MessageContext context) {
        LogService logger = ServiceLocator.locate(LogService.class);
        logger.info("Initialize processor VpsDocIdMessageProcessor");
        String docId = context.getMessage().getAttribute("docId", MessageAttributeValueType.STRING);
        String base30DocumentId = context.getMessage().getAttribute("base30DocumentId", MessageAttributeValueType.STRING);
        String apiConnection = context.getMessage().getAttribute("apiConnection", MessageAttributeValueType.STRING);
        updateAllVersions(docId, base30DocumentId, apiConnection);
    }

    /**
     * updatePreviousVersions : Queue each version
     *
     * @param docId
     * @param base30DocumentId
     */
    public void updateAllVersions(String docId, String base30DocumentId, String apiConnection) {

        VpsVQLHelper vqlHelper = new VpsVQLHelper();
        VpsAPIClient apiClient = new VpsAPIClient(apiConnection);
        LogService logger = ServiceLocator.locate(LogService.class);
        
        vqlHelper.appendVQL("SELECT " + DOCFIELD_MAJOR_VERSION_NUMBER + "," +
                DOCFIELD_MINOR_VERSION_NUMBER + "," + DOCFIELD_BASE30_DOCUMENT_ID +","+ DOCFIELD_BINDER);
        vqlHelper.appendVQL(" FROM " + "allversions documents");
        vqlHelper.appendVQL(" WHERE " + DOCFIELD_ID + "=" + docId);
        QueryResponse versionResponse = vqlHelper.runVQL();
        //update previous versions one by one
        versionResponse.streamResults().forEach(versionResult -> {
            BigDecimal majorVersionNumber = versionResult.getValue(DOCFIELD_MAJOR_VERSION_NUMBER, ValueType.NUMBER);
            BigDecimal minorVersionNumber = versionResult.getValue(DOCFIELD_MINOR_VERSION_NUMBER, ValueType.NUMBER);
            String existingDocId = getNotNullValue(versionResult.getValue(DOCFIELD_BASE30_DOCUMENT_ID, ValueType.STRING));
            boolean isBinder = versionResult.getValue(DOCFIELD_BINDER, ValueType.BOOLEAN);

            Map<String, String> documentFieldsToUpdate = VaultCollections.newMap();
            documentFieldsToUpdate.put(DOCFIELD_BASE30_DOCUMENT_ID, base30DocumentId);
            documentFieldsToUpdate.put(DOCFIELD_EXPORT_FILENAME, base30DocumentId);

            if (existingDocId.equals("")) {
                boolean updateSuccess = false;
                if (isBinder) {
                    updateSuccess = apiClient.updateBinderFields(docId, majorVersionNumber.toString(),
                            minorVersionNumber.toString(), documentFieldsToUpdate);
                } else {
                    updateSuccess = apiClient.updateDocumentFields(docId, majorVersionNumber.toString(),
                            minorVersionNumber.toString(), documentFieldsToUpdate);
                }
                if (!updateSuccess) {
                    logger.error("Failed to update binder/document with id {}", docId + "_" + majorVersionNumber +
                            "_" + minorVersionNumber);
                } else {
                    logger.info("Successfully updated binder/document with id {}", docId + "_" + majorVersionNumber +
                            "_" + minorVersionNumber);
                }
            }

        });
    }

    public String getNotNullValue(String value) {
        if (value == null) {
            value = "";
        }
        return value;
    }
}