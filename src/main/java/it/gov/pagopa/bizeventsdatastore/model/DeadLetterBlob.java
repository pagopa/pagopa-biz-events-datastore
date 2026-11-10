package it.gov.pagopa.bizeventsdatastore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * A blob saved in a dead letter container
 */
@Getter
@AllArgsConstructor
public class DeadLetterBlob {

    private final String name;
    private final Map<String, String> metadata;
}
