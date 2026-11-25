package it.gov.pagopa.bizeventsdatastore.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * A blob saved in a dead letter container
 */
@Getter
public class DeadLetterBlob {

    private final String name;
    /**
     * Mutable copy of the blob metadata: the recovery updates it before saving it on the blob
     */
    private final Map<String, String> metadata;

    public DeadLetterBlob(String name, Map<String, String> metadata) {
        this.name = name;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }
}
