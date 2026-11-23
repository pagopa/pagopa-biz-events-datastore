package it.gov.pagopa.bizeventsdatastore.exception;

import lombok.Getter;

/**
 * Reasons why the recovery of a views dead letter blob failed, saved in the recoveryReason metadata of the blob
 */
@Getter
public enum DeadLetterRecoveryError {
    INVALID_VIEWS("The views generated from the biz-event are not valid"),
    BIZ_EVENT_NOT_FOUND("The biz-event of the views is not on the datastore or is not in DONE or INGESTED status"),
    COSMOS_ERROR("Error reading or writing on Cosmos DB"),
    BLOB_READ_ERROR("The dead letter blob could not be downloaded or its content is not a list of biz-events"),
    BLOB_UPDATE_ERROR("The metadata of the dead letter blob could not be updated"),
    BLOB_DELETE_ERROR("The events of the dead letter blob have been recovered, but the blob could not be deleted"),
    UNEXPECTED_ERROR("Unexpected error recovering the views of the biz-event");

    private final String details;

    DeadLetterRecoveryError(String details) {
        this.details = details;
    }
}
