package it.gov.pagopa.bizeventsdatastore.exception;

/** Thrown in case no biz event is found in the CosmosDB container */
public class BizEventNotValidForViewGenerationException extends Exception{

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventNotValidForViewGenerationException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public BizEventNotValidForViewGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}


