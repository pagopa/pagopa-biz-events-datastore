package it.gov.pagopa.bizeventsdatastore.exception;

import lombok.Getter;

/**
 * Reasons why the recovery of a dead letter blob failed, saved in the recoveryReason metadata of the blob
 */
@Getter
public enum DeadLetterRecoveryError {
    MISSING_PAYMENT_KEY("The biz-event has no creditor idPA or debtor position IUV"),
    SAME_PAYMENT_OF_OTHER_BIZ_EVENT("A biz-event with a different id and the same idPA and IUV is already on the datastore"),
    INVALID_VIEWS("The views generated from the biz-event are not valid"),
    BIZ_EVENT_NOT_FOUND("The biz-event of the views is not on the datastore or is not in DONE or INGESTED status"),
    COSMOS_ERROR("Error reading or writing on Cosmos DB"),
    PDND_PUBLICATION_ERROR("The created biz-events have not been sent to the PDND Event Hub"),
    BLOB_READ_ERROR("The dead letter blob could not be downloaded or its content is not a list of biz-events"),
    BLOB_UPDATE_ERROR("The metadata of the dead letter blob could not be updated"),
    BLOB_DELETE_ERROR("The events of the dead letter blob have been recovered, but the blob could not be deleted"),
    UNEXPECTED_ERROR("Unexpected error recovering the biz-event");

    private final String details;

    DeadLetterRecoveryError(String details) {
        this.details = details;
    }
}
