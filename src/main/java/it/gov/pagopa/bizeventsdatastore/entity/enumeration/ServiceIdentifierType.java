package it.gov.pagopa.bizeventsdatastore.entity.enumeration;

import java.util.Arrays;

/**
 * Enum for transaction origin
 */
public enum ServiceIdentifierType {
    INTERNAL, PM, NDP001PROD , NDP002PROD, NDP003PROD, UNKNOWN;

    public static boolean isValidServiceIdentifier(String origin) {
        return Arrays.stream(values()).anyMatch(it -> it.name().equalsIgnoreCase(origin));
    }
}
