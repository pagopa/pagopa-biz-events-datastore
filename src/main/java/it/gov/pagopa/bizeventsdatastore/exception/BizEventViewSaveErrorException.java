package it.gov.pagopa.bizeventsdatastore.exception;

/** Thrown in case an error occur while saving a biz event view */
public class BizEventViewSaveErrorException extends Exception{

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventViewSaveErrorException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public BizEventViewSaveErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}


