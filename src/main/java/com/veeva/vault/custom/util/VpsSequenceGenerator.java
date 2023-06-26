/*
 * --------------------------------------------------------------------
 * MessageProcessor:	VpsDocIDGenerator
 * Author:				achinchalkar @ Veeva
 * Date:				2020-05-24
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 *		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.util;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
//import org.apache.commons.lang3.StringUtils;


@UserDefinedClassInfo()
public class VpsSequenceGenerator {

    /**
     * Generate special base30 numbering based on client's legacy system
     * @param base10Id
     * @param numberWidth
     * @param padding
     * @return based30 String
     */
    public String getBase30Number(int base10Id, int numberWidth, String padding) {
        //Use Base 30 numbering system without number �one� and any vowels
        char[] BASE30_ALPHABET = new char[]{'0', '2', '3', '4', '5', '6', '7', '8', '9', 'B', 'C', 'D', 'F', 'G', 'H',
                'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'};

        int quotient = base10Id;
        String base30Id = "";
        int base = BASE30_ALPHABET.length;
        while (quotient >= BASE30_ALPHABET.length) {
            int base30Index = (int) (quotient % base);
            base30Id = String.valueOf(BASE30_ALPHABET[base30Index]) + base30Id;
            quotient = quotient / base;
        }
        base30Id = String.valueOf(BASE30_ALPHABET[(int) (quotient)]) + base30Id;
        while (base30Id.length() < numberWidth) {
            base30Id = padding + base30Id;
        }
//        String base30IdFormatted= StringUtils.leftPad(base30Id, numberWidth, padding);
        return base30Id;
    }
}