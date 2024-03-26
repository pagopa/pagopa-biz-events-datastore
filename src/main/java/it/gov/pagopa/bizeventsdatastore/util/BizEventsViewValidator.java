package it.gov.pagopa.bizeventsdatastore.util;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class BizEventsViewValidator {
	
	private BizEventsViewValidator() {}
	
	public static boolean validate(Logger logger, BizEventToViewResult bizEventToViewResult, BizEvent bizEvent) throws AppException {
		ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<BizEventToViewResult>> violations = validator.validate(bizEventToViewResult);

		if (!violations.isEmpty()) {
			for(ConstraintViolation<BizEventToViewResult> v : violations) {
				logger.log(Level.SEVERE, () -> "BizEventToView constraint violation [BizEvent id="+bizEvent.getId()+", "+v.getLeafBean().getClass()+", property:" + v.getPropertyPath() + ", value:"+  v.getInvalidValue() +", constraints: "+ v.getMessageTemplate() +"]");
			}
			
			throw new AppException("Error during BizEventToView validation [BizEvent id="+bizEvent.getId()+"]");
		}

		return true;
	}

}
