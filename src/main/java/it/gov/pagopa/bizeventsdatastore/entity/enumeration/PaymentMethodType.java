package it.gov.pagopa.bizeventsdatastore.entity.enumeration;

import java.util.Arrays;

/**
 * Enum for transaction payment methods
 */
public enum PaymentMethodType {

    BBT, BP, AD, CP, PO, OBEP, JIF, MYBK, PPAL, UNKNOWN;

    public static boolean isValidPaymentMethod(String origin) {
        return Arrays.stream(values()).anyMatch(it -> it.name().equalsIgnoreCase(origin));
    }
}
