package com.veeva.vault.custom.model;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;

import java.util.List;

@UserDefinedClassInfo
public class TemplateGroup {
    private String templateGroupId;
    private List<String> listURS;
    private String objectName; // object name of the user role setup object (the label)
    private String objectNamePL; // object name of the user role setup object (the picklist)
    private String userAPIName; // the user field api name on the user role setup
    private String countryAPIName; // optional - api name of the country field on user role setup object
    private List<UserRoleTemplateMapping> urtm;
    private List<Record> templates;

    public TemplateGroup(String templateGroupId) {
        setTemplateGroupId(templateGroupId);
        setUserAPIName("user__v");
    }

    public TemplateGroup(String templateGroupId, List<String> listURS) {
        setTemplateGroupId(templateGroupId);
        setListURS(listURS);
        setUserAPIName("user__v");
    }

    public String getTemplateGroupId() {
        return templateGroupId;
    }
    public void setTemplateGroupId(String templateGroupId) { this.templateGroupId = (templateGroupId == null ? "" : templateGroupId); }

    public List<String> getListURS() {
        return listURS;
    }
    public void setListURS(List<String> l) {
        listURS = VaultCollections.newList();

        if (l != null && l.size() > 0)
            this.listURS = l;
    }

    public String getObjectName() {
        return objectName;
    }
    public void setObjectName(String objectName) { this.objectName = (objectName == null ? "" : objectName); }

    public String getObjectNamePL() {
        return objectNamePL;
    }
    public void setObjectNamePL(String objectNamePL) { this.objectNamePL = (objectNamePL == null ? "" : objectNamePL); }

    public String getUserAPIName() { return userAPIName;}
    public void setUserAPIName(String userAPIName) { this.userAPIName = (userAPIName == null ? "" : userAPIName); }

    public String getCountryAPIName() {
        return countryAPIName;
    }

    public void setCountryAPIName(String countryAPIName) { this.countryAPIName = (countryAPIName == null ? "" : countryAPIName); }

    public List<UserRoleTemplateMapping> getUrtm() {
        return urtm;
    }

    public void setUrtm(List<UserRoleTemplateMapping> urtm) {
        this.urtm = urtm;
    }

    public List<Record> getTemplates() {
        return templates;
    }

    public void setTemplates(List<Record> templates) {
        this.templates = templates;
    }
}