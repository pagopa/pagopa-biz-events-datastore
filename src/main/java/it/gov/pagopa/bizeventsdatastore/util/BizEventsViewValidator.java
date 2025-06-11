package it.gov.pagopa.bizeventsdatastore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class BizEventsViewValidator {

    private BizEventsViewValidator() {
    }

    public static void validate(
            Logger logger,
            BizEventToViewResult bizEventToViewResult,
            BizEvent bizEvent
    ) throws BizEventToViewConstraintViolationException {
        ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<BizEventToViewResult>> violations = validator.validate(bizEventToViewResult);

        if (!violations.isEmpty()) {
            List<String> errMsgList = new ArrayList<>();
            for (ConstraintViolation<BizEventToViewResult> v : violations) {
                String message = String.format(
                        "BizEventToView constraint violation [BizEvent id=%s, %s, property:%s, value:%s, constraints: %s]",
                        bizEvent.getId(),
                        v.getLeafBean().getClass(),
                        v.getPropertyPath(),
                        v.getInvalidValue(),
                        v.getMessageTemplate()
                );
                errMsgList.add(message);
                logger.log(Level.SEVERE, message);
            }

            throw new BizEventToViewConstraintViolationException("Error during BizEventToView validation,  [BizEvent id=" + bizEvent.getId() + "]", errMsgList);
        }
    }
}
