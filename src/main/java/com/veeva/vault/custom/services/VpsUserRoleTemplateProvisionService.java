/*
 * --------------------------------------------------------------------
 * UserDefinedService: VpsUserRoleTemplateProvisionService
 * Author: bryanchan @ Veeva
 * Created Date:        2020-08-27
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:	Interface for various User Role Template Service
 *---------------------------------------------------------------------
 * Revision:
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved the exact same logic from UserRoleTemplateAssignmentProvision
 *    and UserRoleTemplateProvisionChanges to UDS to free up memory limits.
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.services;

import com.veeva.vault.custom.model.UserRoleTemplateMapping;
import com.veeva.vault.sdk.api.core.UserDefinedService;
import com.veeva.vault.sdk.api.core.UserDefinedServiceInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.query.QueryResult;

import java.util.List;
import java.util.Map;

@UserDefinedServiceInfo
public interface VpsUserRoleTemplateProvisionService extends UserDefinedService {

    /**
     * Determines if a record should be deleted from User Role Template Provisioning
     *
     * @param urtmf User Role Template Mapping
     * @param queryResult - query results
     * @param recTemplate - the record template
     * @param countryAPIName - the country api name
     * @param countryValue - the country value
     * @return true if the record can be deleted.
     */
    public boolean isDelete(Map<String, UserRoleTemplateMapping> urtmf,
                     QueryResult queryResult, Record recTemplate, String countryAPIName, String countryValue);

    public List<Record> getNewUserRecords(Record recContext, List<UserRoleTemplateMapping> urtm,
                                          List<Record> templates, String objectName, String userAPIName);

    public void refreshUserProvisioningRecords(String templateGroup);
}