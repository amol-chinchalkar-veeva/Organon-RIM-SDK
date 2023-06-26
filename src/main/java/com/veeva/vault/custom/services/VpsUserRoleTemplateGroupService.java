/*
 * --------------------------------------------------------------------
 * UserDefinedService: VpsUserRoleTemplateGroupService
 * Author: bryanchan @ Veeva
 * Created Date:        2021-08-18
 * Last Modifed Date:   2021-08-18
 *---------------------------------------------------------------------
 * Description:	Interface for various User Role Template Group Service
 *---------------------------------------------------------------------
 * Revision:
 * 2021-08-18: R2.9.1 - bryan.chan@veeva:
 *    Moved the exact same logic from UserRoleTemplateAssignmentProvision
 *    and UserRoleTemplateProvisionChanges to UDS to free up memory limits.
 *---------------------------------------------------------------------
 * Copyright (c) 2021 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.services;

import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.custom.model.UserRoleTemplateMapping;
import com.veeva.vault.sdk.api.core.UserDefinedService;
import com.veeva.vault.sdk.api.core.UserDefinedServiceInfo;
import com.veeva.vault.sdk.api.data.Record;


import java.util.List;
import java.util.Map;

@UserDefinedServiceInfo
public interface VpsUserRoleTemplateGroupService extends UserDefinedService {
    public void initTemplateGroup(TemplateGroup templateGroup);
    public List<Record> getNewUserRecords(TemplateGroup templateGroup, Record recContext);
    public List<Record> getExistingURSRecords(TemplateGroup templateGroup, String userId, String countryValue, String countryAPIName);
    public Map<String, UserRoleTemplateMapping> getURTMByURSField(TemplateGroup templateGroup);

}