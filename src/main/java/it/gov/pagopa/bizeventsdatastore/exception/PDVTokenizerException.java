package it.gov.pagopa.bizeventsdatastore.exception;

import lombok.Getter;

/**
 * Thrown in case an error occur when invoking PDV Tokenizer service
 */
@Getter
public class PDVTokenizerException extends Exception {

    private final int statusCode;

    /**
     * Constructs new exception with provided message and status code
     *
     * @param message Detail message
     * @param statusCode status code
     */
    public PDVTokenizerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Constructs new exception with provided message, status code and cause
     *
     * @param message Detail message
     * @param statusCode status code
     * @param cause Exception causing the constructed one
     */
    public PDVTokenizerException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
