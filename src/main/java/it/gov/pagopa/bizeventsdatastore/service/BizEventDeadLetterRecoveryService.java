package it.gov.pagopa.bizeventsdatastore.service;

/**
 * Service that recovers the events saved in the views dead letter container
 */
public interface BizEventDeadLetterRecoveryService {

    /**
     * Recovers the views of the biz-events saved in the views dead letter, generating them from the biz-event on the datastore.
     *
     * @param invocationId the invocation id of the current execution context
     */
    void recoverBizEventViews(String invocationId);
}
