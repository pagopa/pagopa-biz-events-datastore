package it.gov.pagopa.bizeventsdatastore.exception;

import java.util.List;

public class BizEventToViewConstraintViolationException extends Exception {

	private final List<String> errorMessages;

	/**
	 * generated serialVersionUID
	 */
	private static final long serialVersionUID = -7564079264281462536L;
	
	public BizEventToViewConstraintViolationException(String message, List<String> errorMessages) {
		super(message);
        this.errorMessages = errorMessages;
    }

	public List<String> getErrorMessages() {
		return errorMessages;
	}
}
