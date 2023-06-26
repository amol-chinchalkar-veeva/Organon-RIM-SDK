package com.veeva.vault.custom.services;

import com.veeva.vault.custom.model.TemplateGroup;
import com.veeva.vault.sdk.api.core.UserDefinedService;
import com.veeva.vault.sdk.api.core.UserDefinedServiceInfo;
import com.veeva.vault.sdk.api.data.Record;

import java.util.Set;

@UserDefinedServiceInfo
public interface VpsUserRoleProvisioningSetupDataService extends UserDefinedService {
    public void initTemplateGroup(TemplateGroup templateGroup);
    public void deleteExistingRecords(TemplateGroup templateGroup, Set<String> setUsers, Record recTemplate);
    public Set<String> getCurrentUsers(String templateGroup);
}