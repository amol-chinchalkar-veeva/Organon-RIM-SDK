/*
 * --------------------------------------------------------------------
 * UserDefinedClass: UserRoleTemplateMapping
 * Author: Todd Taylor
 * Created Date:        2017-07-27
 * Last Modifed Date:   2020-08-27
 *---------------------------------------------------------------------
 * Description:	A class that represents a User Role Tempalte Mapping
 *---------------------------------------------------------------------
 * Revision:
 * 2020-08-27: R2.9.1 - bryan.chan@veeva:
 *    Moved the exact same inner class from various locations to it's own class
 *    to remove duplicate code.
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 *
 */
package com.veeva.vault.custom.model;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

@UserDefinedClassInfo()
public class UserRoleTemplateMapping  {
    private String template_field__c;
    private String user_role_setup_field__c;
    private boolean is_picklist__c;

    public UserRoleTemplateMapping(String template_field__c,String user_role_setup_field__c,boolean is_picklist__c) {
        this.template_field__c = template_field__c;
        this.user_role_setup_field__c = user_role_setup_field__c;
        this.is_picklist__c = is_picklist__c;
    }

    public String getTemplate_field__c() {
        return this.template_field__c;
    }

    public String getUser_role_setup_field() {
        return this.user_role_setup_field__c;
    }

    public boolean is_picklist__c() {
        return this.is_picklist__c;
    }
}