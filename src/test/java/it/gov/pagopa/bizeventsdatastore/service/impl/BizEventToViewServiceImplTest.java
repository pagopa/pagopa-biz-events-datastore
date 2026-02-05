package it.gov.pagopa.bizeventsdatastore.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.Creditor;
import it.gov.pagopa.bizeventsdatastore.entity.Debtor;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.Info;
import it.gov.pagopa.bizeventsdatastore.entity.InfoECommerce;
import it.gov.pagopa.bizeventsdatastore.entity.Payer;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;
import it.gov.pagopa.bizeventsdatastore.entity.Psp;
import it.gov.pagopa.bizeventsdatastore.entity.Transaction;
import it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.entity.TransactionPsp;
import it.gov.pagopa.bizeventsdatastore.entity.Transfer;
import it.gov.pagopa.bizeventsdatastore.entity.User;
import it.gov.pagopa.bizeventsdatastore.entity.WalletItem;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.ServiceIdentifierType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import it.gov.pagopa.bizeventsdatastore.entity.view.UserDetail;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class BizEventToViewServiceImplTest {

    private static final String VALID_PAYER_CF = "JHNDOE80D45E507N";
    private static final String VALID_USER_CF = "MNRTLE80D45E507N";
    private static final String INVALID_CF = "an invalid fiscal code";
    private static final String VALID_DEBTOR_CF = "JHNDOE80D05B157Y";
    private static final String REMITTANCE_INFORMATION_FORMATTED = "CB617RP-Mag2022/Apr2023--EC Lorem-E. 261,92 (san 4,91 int 0,95)";

    @Spy
    private BizEventToViewServiceImpl sut;
    
    @Mock
    private ExecutionContext context;

    @Test
    void mapBizEventToViewSuccess() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());
    	
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("10.00").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().clientId("IO").type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").amount(1000).build())
                        .build())
                .properties(properties)
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());
        assertEquals(false, result.getUserViewList().get(0).isHidden());
        assertEquals(false, result.getUserViewList().get(1).isHidden());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.NDP001PROD, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
    }
    
    @Test
    void NDP004PROD_mapBizEventToViewSuccess() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP004PROD.name());
    	
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("10.00").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().clientId("IO").type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").amount(1000).build())
                        .build())
                .properties(properties)
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());
        assertEquals(false, result.getUserViewList().get(0).isHidden());
        assertEquals(false, result.getUserViewList().get(1).isHidden());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.NDP004PROD, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
        
    }
    
    @Test
    void mapBizEventToViewNewLineRemittanceInformationSuccess() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
    	
    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", "NDP004IT");
        
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().amount("10.00").build())
                .transferList(Arrays.asList(Transfer.builder()
                		.fiscalCodePA("00493410583")
                		.companyName("ACI Automobile Club Italia")
                		.amount("1000")
                		.transferCategory("9/0301105TS/3/CB617RP")
                		.remittanceInformation("/RFB/9600000000/TXT/CB617RP-Mag2022/Apr2023--EC Lorem-E. 261,92 (san 4,91 int 0,95)")
                		.build()))
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().origin("IO").rrn("rrn").creationDate("21-03-2024").amount(1000).build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());
        assertEquals(false, result.getUserViewList().get(0).isHidden());
        assertEquals(false, result.getUserViewList().get(1).isHidden());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.UNKNOWN, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION_FORMATTED, result.getCartView().getSubject());
        
        // set remittance information with new line characters 
        bizEvent.getTransferList().get(0).setRemittanceInformation("/RFB/9600000000/TXT/"+ System.lineSeparator() +"CB617RP-Mag2022/"+ System.lineSeparator() +"Apr2023--EC Lorem-E. 261,92 (san 4,91 int 0,95)");
        result = sut.mapBizEventToView(logger, bizEvent);
        assertEquals(REMITTANCE_INFORMATION_FORMATTED, result.getCartView().getSubject());
    }

	

    @Test
    void mapBizEventToViewSuccessEventWithSameDebtorAndPayer() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("user-name user-surname")
                        .entityUniqueIdentifierValue(VALID_USER_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("10.00").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().origin("CHECKOUT").rrn("rrn").creationDate("21-03-2024").amount(1000).build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(1, result.getUserViewList().size());

        assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
        assertTrue(result.getUserViewList().get(0).isPayer());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(false, result.getUserViewList().get(0).isHidden());

        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_USER_CF, result.getCartView().getDebtor().getTaxCode());
    }

    @Test
    void mapBizEventToViewSuccessOnlyDebtor() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("1000").build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(1, result.getUserViewList().size());

        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());

        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertNull(result.getGeneralView().getPayer());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
    }

    @Test
    void mapBizEventToViewSuccessOnlyPayer() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .payer(Payer.builder()
                        .fullName("payer")
                        .entityUniqueIdentifierValue(VALID_PAYER_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("1000").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(1, result.getUserViewList().size());

        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertNull(result.getCartView().getDebtor());
    }
    
    @Test
    void mapBizEventToViewModCart1False() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());
    	
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("100.50").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().clientId("IO").type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").amount(10050).build())
                        .build())
                .properties(properties)
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());
        assertEquals(false, result.getUserViewList().get(0).isHidden());
        assertEquals(false, result.getUserViewList().get(1).isHidden());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.NDP001PROD, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
    }
    
    @Test
    void mapBizEventToViewModCart1True() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());
    	
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("26.4").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().clientId("IO").type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").amount(4240).build())
                        .build())
                .properties(properties)
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());
        assertEquals(true, result.getUserViewList().get(0).isHidden());
        assertEquals(true, result.getUserViewList().get(1).isHidden());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.NDP001PROD, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
    }
    
    @Test
    void mapBizEventToViewModCart1TrueByJSONFile() throws BizEventToViewConstraintViolationException, IOException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");

    	Map<String, Object> properties = new HashMap<>(); 
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());

    	BizEvent bizEvent = TestUtil.readModelFromFile("payment-manager/bizEventModCart1.json", BizEvent.class);

    	BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);


    	assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
    	assertFalse(result.getUserViewList().get(0).isPayer());

    	assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
    	assertEquals(true, result.getUserViewList().get(0).isHidden());

    	assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
    	assertEquals(1, result.getGeneralView().getTotalNotice());
    	assertEquals(ServiceIdentifierType.NDP003PROD, result.getGeneralView().getOrigin());

    	assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
    	assertEquals(bizEvent.getId(), result.getCartView().getEventId());
    	assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
    	assertEquals(VALID_USER_CF, result.getCartView().getDebtor().getTaxCode());
    }


    @Test
    void mapBizEventToViewCheckoutUserNotRegisteredShouldBeHidden() throws BizEventToViewConstraintViolationException {
        Logger logger = Logger.getLogger("BizEventToViewService-test-logger");

        Map<String, Object> properties = new HashMap<>();
        properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());

        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .psp(Psp.builder().psp("psp value").build())
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").totalNotice("1").amount("10.0").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .type(UserType.GUEST)
                                .build())
                        .info(InfoECommerce.builder().clientId("CHECKOUT").type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").amount(10000).build())
                        .build())
                .properties(properties)
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        this.checkGeneratedViewResult(result);

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
            assertEquals(true, result.getUserViewList().get(0).isHidden());
            assertEquals(false, result.getUserViewList().get(1).isHidden());
        } else {
            assertEquals(VALID_USER_CF, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(VALID_DEBTOR_CF, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
            assertEquals(false, result.getUserViewList().get(0).isHidden());
            assertEquals(true, result.getUserViewList().get(1).isHidden());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(VALID_USER_CF, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());
        assertEquals(ServiceIdentifierType.NDP001PROD, result.getGeneralView().getOrigin());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(VALID_DEBTOR_CF, result.getCartView().getDebtor().getTaxCode());
    }

    @Test
    void mapBizEventToViewFailNoDebtorAndUser() throws BizEventToViewConstraintViolationException {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(logger, bizEvent);

        assertNull(result);
    }
    
    
    @Test
    void mapBizEventToViewValidationFail() {
    	Logger logger = Logger.getLogger("BizEventToViewService-test-logger");
        
        // event without mandatory psp value
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder().modelType("2").noticeNumber("1234567890").build())
                .paymentInfo(PaymentInfo.builder().remittanceInformation("remittance information").amount("1000").build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .transaction(Transaction.builder().rrn("rrn").creationDate("21-03-2024").build())
                        .build())
                .build();

        assertThrows(BizEventToViewConstraintViolationException.class, () -> sut.mapBizEventToView(logger,bizEvent));
    }

    @Test
    void getPaymentMethodSuccess() {
        PaymentInfo paymentInfo = PaymentInfo.builder().paymentMethod(PaymentMethodType.BP.name()).build();
        PaymentMethodType result = sut.getPaymentMethod(paymentInfo);
        assertEquals(PaymentMethodType.BP, result);
    }

    @Test
    void getPaymentMethodWithInvalidMethodValueReturnDefault() {
        PaymentInfo paymentInfo = PaymentInfo.builder().paymentMethod("invalid method").build();
        PaymentMethodType result = sut.getPaymentMethod(paymentInfo);
        assertEquals(PaymentMethodType.UNKNOWN, result);
    }

    @Test
    void getPaymentMethodWithNullMethodReturnDefault() {
        PaymentInfo paymentInfo = PaymentInfo.builder().build();
        PaymentMethodType result = sut.getPaymentMethod(paymentInfo);
        assertEquals(PaymentMethodType.UNKNOWN, result);
    }

    @Test
    void getServiceIdentifierForPMSuccess() {
    	Map<String, Object> properties = new HashMap<>();
    	properties.put("serviceIdentifier", ServiceIdentifierType.PM.name());
        ServiceIdentifierType result = sut.getServiceIdentifier(properties);
        assertEquals(ServiceIdentifierType.PM, result);
    }

    @Test
    void getServiceIdentifierSuccess() {
    	Map<String, Object> properties = new HashMap<>();
    	properties.put("serviceIdentifier", ServiceIdentifierType.NDP001PROD.name());
        ServiceIdentifierType result = sut.getServiceIdentifier(properties);
        assertEquals(ServiceIdentifierType.NDP001PROD, result);
    }

    @Test
    void getServiceIdentifierWithUnknownValuePropertiesReturnDefault() {
    	Map<String, Object> properties = new HashMap<>();
    	properties.put("serviceIdentifier", "NDP004IT");
    	ServiceIdentifierType result = sut.getServiceIdentifier(properties);
        assertEquals(ServiceIdentifierType.UNKNOWN, result);
    }
    
    @Test
    void getServiceIdentifierWithEmptyPropertiesReturnDefault() {
        ServiceIdentifierType result = sut.getServiceIdentifier(new HashMap<String, Object>());
        assertEquals(ServiceIdentifierType.UNKNOWN, result);
    }

    @Test
    void getTransactionIdWithNullTotalNotice() {
        BizEvent bizEvent = BizEvent.builder().id("biz-id").build();
        String result = sut.getTransactionId(bizEvent);
        assertEquals(bizEvent.getId(), result);
    }

    @Test
    void getTransactionIdWithTotalNotice1() {
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .paymentInfo(PaymentInfo.builder()
                        .totalNotice("1")
                        .build())
                .build();
        String result = sut.getTransactionId(bizEvent);
        assertEquals(bizEvent.getId(), result);
    }

    @Test
    void getTransactionIdWithTotalNoticeBiggerThan1() {
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .paymentInfo(PaymentInfo.builder()
                        .totalNotice("4")
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .transactionId("transacation-id")
                                .build())
                        .info(InfoECommerce.builder().type("PPAL").maskedEmail("xxx@xxx.it").build())
                        .build())
                .build();
        String result = sut.getTransactionId(bizEvent);
        assertEquals(bizEvent.getTransactionDetails().getTransaction().getTransactionId(), result);
    }

    @Test
    void getTransactionIdWithTotalNoticeBiggerThan1ButTransactionIdIsNull() {
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .paymentInfo(PaymentInfo.builder()
                        .totalNotice("4")
                        .build())
                .transactionDetails(TransactionDetails.builder().build())
                .build();
        String result = sut.getTransactionId(bizEvent);
        assertNull(result);
    }

    @Test
    void getAuthCodeSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .transaction(Transaction.builder()
                        .numAut("1324")
                        .build())
                .build();
        String result = sut.getAuthCode(transactionDetails);
        assertEquals(transactionDetails.getTransaction().getNumAut(), result);
    }

    @Test
    void getAuthCodeNotPresent() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        String result = sut.getAuthCode(transactionDetails);
        assertNull(result);
    }

    @Test
    void getRrnFromTransactionDetailsSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .rrn("rrn")
                                .build())
                        .build())
                .build();
        String result = sut.getRrn(bizEvent);
        assertEquals(bizEvent.getTransactionDetails().getTransaction().getRrn(), result);
    }

    @Test
    void getRrnFromPaymentTokenSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .paymentToken("payment-token")
                        .build())
                .build();
        String result = sut.getRrn(bizEvent);
        assertEquals(bizEvent.getPaymentInfo().getPaymentToken(), result);
    }

    @Test
    void getRrnFromIurSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .IUR("iur")
                        .build())
                .build();
        String result = sut.getRrn(bizEvent);
        assertEquals(bizEvent.getPaymentInfo().getIUR(), result);
    }

    @Test
    void getRrnNotPresent() {
        BizEvent bizEvent = BizEvent.builder().build();
        String result = sut.getRrn(bizEvent);
        assertNull(result);
    }

    @Test
    void getPspNameFromTransactionDetailsSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .psp(TransactionPsp.builder()
                                        .businessName("business-name")
                                        .build())
                                .build())
                        .build())
                .build();
        String result = sut.getPspName(bizEvent);
        assertEquals(bizEvent.getTransactionDetails().getTransaction().getPsp().getBusinessName(), result);
    }

    @Test
    void getPspNameFromPspSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .psp(Psp.builder()
                        .psp("psp")
                        .build())
                .build();
        String result = sut.getPspName(bizEvent);
        assertEquals(bizEvent.getPsp().getPsp(), result);
    }

    @Test
    void getPspNameNotPresent() {
        BizEvent bizEvent = BizEvent.builder().build();
        String result = sut.getPspName(bizEvent);
        assertNull(result);
    }

    @Test
    void getTransactionDateFromTransactionDetailsSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .creationDate("2023-11-14T18:31:55Z")
                                .build())
                        .build())
                .build();
        String result = sut.getTransactionDate(bizEvent);
        assertEquals(bizEvent.getTransactionDetails().getTransaction().getCreationDate(), result);
    }

    @Test
    void getTransactionDateFromPaymentInfoSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime("2023-11-14T19:31:55.484065")
                        .build())
                .build();
        String result = sut.getTransactionDate(bizEvent);
        assertEquals(bizEvent.getPaymentInfo().getPaymentDateTime(), result);
    }

    @Test
    void getTransactionDateNotPresent() {
        BizEvent bizEvent = BizEvent.builder().build();
        String result = sut.getTransactionDate(bizEvent);
        assertNull(result);
    }

    @Test
    void getAccountHolderSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .wallet(WalletItem.builder()
                        .info(Info.builder()
                                .holder("holder")
                                .build())
                        .build())
                .build();
        String result = sut.getPaymentMethodAccountHolder(transactionDetails);
        assertEquals(transactionDetails.getWallet().getInfo().getHolder(), result);
    }

    @Test
    void getAccountHolderNotPresent() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        String result = sut.getPaymentMethodAccountHolder(transactionDetails);
        assertNull(result);
    }

    @Test
    void getBrandSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .wallet(WalletItem.builder()
                        .info(Info.builder()
                                .brand("brand")
                                .build())
                        .build())
                .build();
        String result = sut.getBrand(transactionDetails);
        assertEquals(transactionDetails.getWallet().getInfo().getBrand(), result);
    }

    @Test
    void getBrandNotPresent() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        String result = sut.getBrand(transactionDetails);
        assertNull(result);
    }

    @Test
    void getBlurredNumberSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .wallet(WalletItem.builder()
                        .info(Info.builder()
                                .blurredNumber("blurred-number")
                                .build())
                        .build())
                .build();
        String result = sut.getBlurredNumber(transactionDetails);
        assertEquals(transactionDetails.getWallet().getInfo().getBlurredNumber(), result);
    }

    @Test
    void getBlurredNumberNotPresent() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        String result = sut.getBlurredNumber(transactionDetails);
        assertNull(result);
    }

    @Test
    void getFeeSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .transaction(Transaction.builder()
                        .fee(100L)
                        .build())
                .build();
        String result = sut.getFee(transactionDetails);
        assertEquals("1,00", result);
    }

    @Test
    void getFeeNotPresent() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        String result = sut.getFee(transactionDetails);
        assertNull(result);
    }

    @Test
    void getPayerInfoNoTransactionDetailsSection() {
        BizEvent bizEvent = BizEvent.builder()
                .payer(Payer.builder()
                        .fullName("payer-name")
                        .entityUniqueIdentifierValue(VALID_PAYER_CF)
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNull(result);
    }

    @Test
    void getPayerFromPayerFailInvalidTaxCode() {
        BizEvent bizEvent = BizEvent.builder()
                .payer(Payer.builder()
                        .fullName("payer-name")
                        .entityUniqueIdentifierValue(INVALID_CF)
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNull(result);
    }

    @Test
    void getPayerFromTransactionDetailsWithoutPayerSectionSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("name")
                                .surname("surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNotNull(result);
        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(payerFullName, result.getName());
        assertEquals(bizEvent.getTransactionDetails().getUser().getFiscalCode(), result.getTaxCode());
    }

    @Test
    void getPayerFromTransactionDetailsWithoutPayerSectionFailInvalidTaxCode() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("name")
                                .surname("surname")
                                .fiscalCode(INVALID_CF)
                                .build())
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNull(result);
    }

    @Test
    void getPayerFromTransactionDetailsWithPayerSectionAndInvalidUserTaxCodeSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .payer(Payer.builder()
                        .fullName("payer-name")
                        .entityUniqueIdentifierValue(VALID_PAYER_CF)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("name")
                                .surname("surname")
                                .fiscalCode(INVALID_CF)
                                .build())
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNull(result);
    }

    @Test
    void getSubjectFromPaymentInfoSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .remittanceInformation("remittance")
                        .build())
                .build();
        String result = sut.getItemSubject(bizEvent);
        assertEquals(bizEvent.getPaymentInfo().getRemittanceInformation(), result);
    }

    @Test
    void getSubjectFromTransferListSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .transferList(Collections.singletonList(
                        Transfer.builder()
                                .remittanceInformation("remittance")
                                .amount("100")
                                .build()
                ))
                .build();
        String result = sut.getItemSubject(bizEvent);
        assertEquals(bizEvent.getTransferList().get(0).getRemittanceInformation(), result);
    }

    @Test
    void getSubjectWithUnwantedRemittanceSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .remittanceInformation("pagamento multibeneficiario")
                        .build())
                .transferList(Collections.singletonList(
                        Transfer.builder()
                                .remittanceInformation("remittance")
                                .amount("100")
                                .build()
                ))
                .build();
        String result = sut.getItemSubject(bizEvent);
        assertEquals(bizEvent.getTransferList().get(0).getRemittanceInformation(), result);
    }

    @Test
    void getSubjectNotPresent() {
        BizEvent bizEvent = BizEvent.builder().build();
        String result = sut.getItemSubject(bizEvent);
        assertNull(result);
    }

    @Test
    void getAmountSuccess() {
        PaymentInfo paymentInfo = PaymentInfo.builder().amount("100").build();
        String result = sut.getItemAmount(paymentInfo);
        assertEquals("100", result);
    }

    @Test
    void getAmountNotPresent() {
        PaymentInfo paymentInfo = PaymentInfo.builder().build();
        String result = sut.getItemAmount(paymentInfo);
        assertNull(result);
    }

    @Test
    void getCreditorSuccess() {
        Creditor creditor = Creditor.builder()
                .companyName("company-name")
                .idPA("id-pa")
                .build();
        UserDetail result = sut.getPayee(creditor);
        assertEquals(creditor.getCompanyName(), result.getName());
        assertEquals(creditor.getIdPA(), result.getTaxCode());
    }

    @Test
    void getCreditorNotPresent() {
        UserDetail result = sut.getPayee(null);
        assertNull(result);
    }

    @Test
    void getRefNumberTypeSuccessIuv() {
        DebtorPosition debtorPosition = DebtorPosition.builder().modelType("1").build();
        String result = sut.getRefNumberType(debtorPosition);
        assertEquals("IUV", result);
    }

    @Test
    void getRefNumberTypeSuccessNoticeNumber() {
        DebtorPosition debtorPosition = DebtorPosition.builder().modelType("2").build();
        String result = sut.getRefNumberType(debtorPosition);
        assertEquals("codiceAvviso", result);
    }

    @Test
    void getRefNumberTypeNotPresent() {
        DebtorPosition debtorPosition = DebtorPosition.builder().build();
        String result = sut.getRefNumberType(debtorPosition);
        assertNull(result);
    }

    @Test
    void getRefNumberValueSuccessIuv() {
        DebtorPosition debtorPosition = DebtorPosition.builder().modelType("1").iuv("iuv").build();
        String result = sut.getRefNumberValue(debtorPosition);
        assertEquals(debtorPosition.getIuv(), result);
    }

    @Test
    void getRefNumberValueSuccessNoticeNumber() {
        DebtorPosition debtorPosition = DebtorPosition.builder().modelType("2").noticeNumber("notice-number").build();
        String result = sut.getRefNumberValue(debtorPosition);
        assertEquals(debtorPosition.getNoticeNumber(), result);
    }

    @Test
    void getRefNumberValueNotPresent() {
        DebtorPosition debtorPosition = DebtorPosition.builder().build();
        String result = sut.getRefNumberValue(debtorPosition);
        assertNull(result);
    }

    @Test
    void getTotalNoticeSuccess() {
        PaymentInfo paymentInfo = PaymentInfo.builder().totalNotice("3").build();
        int result = sut.getTotalNotice(paymentInfo);
        assertEquals(3, result);
    }

    @Test
    void getTotalNoticeNotPresent() {
        PaymentInfo paymentInfo = PaymentInfo.builder().build();
        int result = sut.getTotalNotice(paymentInfo);
        assertEquals(1, result);
    }
    
    private void checkGeneratedViewResult(BizEventToViewResult result) {
		assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(2, result.getUserViewList().size());
	}
}