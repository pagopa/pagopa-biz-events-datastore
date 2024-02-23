package it.gov.pagopa.bizeventsdatastore.util;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class BizEventsViewValidator {
	
	private BizEventsViewValidator() {}
	
	public static boolean validate(Logger logger, BizEventToViewResult bizEventToViewResult) {
		ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<BizEventToViewResult>> violations = validator.validate(bizEventToViewResult);

		if (!violations.isEmpty()) {
			for(ConstraintViolation<BizEventToViewResult> v : violations) {
				logger.log(Level.SEVERE, () -> "BizEventToView is not valid, violation [property:" + v.getPropertyPath() + ", value:"+  v.getInvalidValue() +", constraints: "+ v.getMessageTemplate() +"]");
			}
			return false;
		}

		return true;
	}

}
