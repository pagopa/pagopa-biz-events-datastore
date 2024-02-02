package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.OriginType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import it.gov.pagopa.bizeventsdatastore.entity.view.UserDetail;
import it.gov.pagopa.bizeventsdatastore.exception.PDVTokenizerException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.tokenizer.enumeration.ReasonErrorCode;
import it.gov.pagopa.bizeventsdatastore.service.PDVTokenizerServiceRetryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BizEventToViewServiceImplTest {

    private static final String TOKENIZED_DEBTOR_TAX_CODE = "tokenizedDebtorTaxCode";
    private static final String TOKENIZED_PAYER_TAX_CODE = "tokenizedPayerTaxCode";
    private static final String VALID_PAYER_CF = "JHNDOE80D45E507N";
    private static final String VALID_USER_CF = "MNRTLE80D45E507N";
    private static final String INVALID_CF = "an invalid fiscal code";
    private static final String VALID_DEBTOR_CF = "JHNDOE80D05B157Y";

    private PDVTokenizerServiceRetryWrapper tokenizerServiceRetryMock;

    private BizEventToViewServiceImpl sut;

    @BeforeEach
    void setUp() {
        tokenizerServiceRetryMock = mock(PDVTokenizerServiceRetryWrapper.class);

        sut = spy(new BizEventToViewServiceImpl(tokenizerServiceRetryMock));
    }

    @Test
    void mapBizEventToViewSuccess() throws PDVTokenizerException, JsonProcessingException {
        when(tokenizerServiceRetryMock.generateTokenForFiscalCodeWithRetry(anyString()))
                .thenReturn(TOKENIZED_DEBTOR_TAX_CODE, TOKENIZED_PAYER_TAX_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(2, result.getUserViewList().size());

        if (result.getUserViewList().get(0).isPayer()) {
            assertEquals(TOKENIZED_PAYER_TAX_CODE, result.getUserViewList().get(0).getTaxCode());
            assertTrue(result.getUserViewList().get(0).isPayer());
            assertEquals(TOKENIZED_DEBTOR_TAX_CODE, result.getUserViewList().get(1).getTaxCode());
            assertFalse(result.getUserViewList().get(1).isPayer());
        } else {
            assertEquals(TOKENIZED_PAYER_TAX_CODE, result.getUserViewList().get(1).getTaxCode());
            assertTrue(result.getUserViewList().get(1).isPayer());
            assertEquals(TOKENIZED_DEBTOR_TAX_CODE, result.getUserViewList().get(0).getTaxCode());
            assertFalse(result.getUserViewList().get(0).isPayer());
        }
        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(bizEvent.getId(), result.getUserViewList().get(1).getTransactionId());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(TOKENIZED_PAYER_TAX_CODE, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(TOKENIZED_DEBTOR_TAX_CODE, result.getCartView().getDebtor().getTaxCode());
    }

    @Test
    void mapBizEventToViewSuccessOnlyDebtor() throws PDVTokenizerException, JsonProcessingException {
        when(tokenizerServiceRetryMock.generateTokenForFiscalCodeWithRetry(anyString()))
                .thenReturn(TOKENIZED_DEBTOR_TAX_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .debtor(Debtor.builder()
                        .fullName("debtor")
                        .entityUniqueIdentifierValue(VALID_DEBTOR_CF)
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(1, result.getUserViewList().size());

        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(TOKENIZED_DEBTOR_TAX_CODE, result.getUserViewList().get(0).getTaxCode());

        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertNull(result.getGeneralView().getPayer());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertEquals(bizEvent.getDebtor().getFullName(), result.getCartView().getDebtor().getName());
        assertEquals(TOKENIZED_DEBTOR_TAX_CODE, result.getCartView().getDebtor().getTaxCode());
    }

    @Test
    void mapBizEventToViewSuccessOnlyPayer() throws PDVTokenizerException, JsonProcessingException {
        when(tokenizerServiceRetryMock.generateTokenForFiscalCodeWithRetry(anyString()))
                .thenReturn(TOKENIZED_PAYER_TAX_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .build())
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(bizEvent);

        assertNotNull(result);
        assertNotNull(result.getUserViewList());
        assertNotNull(result.getGeneralView());
        assertNotNull(result.getCartView());
        assertEquals(1, result.getUserViewList().size());

        assertEquals(bizEvent.getId(), result.getUserViewList().get(0).getTransactionId());
        assertEquals(TOKENIZED_PAYER_TAX_CODE, result.getUserViewList().get(0).getTaxCode());

        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(bizEvent.getId(), result.getGeneralView().getTransactionId());
        assertEquals(payerFullName, result.getGeneralView().getPayer().getName());
        assertEquals(TOKENIZED_PAYER_TAX_CODE, result.getGeneralView().getPayer().getTaxCode());
        assertEquals(1, result.getGeneralView().getTotalNotice());

        assertEquals(bizEvent.getId(), result.getCartView().getTransactionId());
        assertEquals(bizEvent.getId(), result.getCartView().getEventId());
        assertNull(result.getCartView().getDebtor());
    }

    @Test
    void mapBizEventToViewFailNoDebtorAndUser() throws PDVTokenizerException, JsonProcessingException {
        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .build();

        BizEventToViewResult result = sut.mapBizEventToView(bizEvent);

        assertNull(result);
    }

    @Test
    void mapBizEventToViewFailTokenizer() throws PDVTokenizerException, JsonProcessingException {
        doThrow(new PDVTokenizerException("Error", ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(tokenizerServiceRetryMock).generateTokenForFiscalCodeWithRetry(anyString());

        BizEvent bizEvent = BizEvent.builder()
                .id("biz-id")
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .name("user-name")
                                .surname("user-surname")
                                .fiscalCode(VALID_USER_CF)
                                .build())
                        .build())
                .build();

        assertThrows(PDVTokenizerException.class, () -> sut.mapBizEventToView(bizEvent));
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
    void getOriginFromTransactionSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .transaction(Transaction.builder()
                        .origin(OriginType.NDP001PROD.name())
                        .build())
                .build();
        OriginType result = sut.getOrigin(transactionDetails);
        assertEquals(OriginType.NDP001PROD, result);
    }

    @Test
    void getOriginFromInfoSuccess() {
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .info(InfoECommerce.builder()
                        .clientId(OriginType.NDP001PROD.name())
                        .build())
                .build();
        OriginType result = sut.getOrigin(transactionDetails);
        assertEquals(OriginType.NDP001PROD, result);
    }

    @Test
    void getOriginWithNullValuesReturnDefault() {
        TransactionDetails transactionDetails = TransactionDetails.builder().build();
        OriginType result = sut.getOrigin(transactionDetails);
        assertEquals(OriginType.UNKNOWN, result);
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
        assertEquals("14 novembre 2023, 19:31:55", result);
    }

    @Test
    void getTransactionDateFromPaymentInfoSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime("2023-11-14T19:31:55.484065")
                        .build())
                .build();
        String result = sut.getTransactionDate(bizEvent);
        assertEquals("14 novembre 2023, 19:31:55", result);
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
    void getPayerFromPayerSuccess() {
        BizEvent bizEvent = BizEvent.builder()
                .payer(Payer.builder()
                        .fullName("payer-name")
                        .entityUniqueIdentifierValue(VALID_PAYER_CF)
                        .build())
                .build();
        UserDetail result = sut.getPayer(bizEvent);
        assertNotNull(result);
        assertEquals(bizEvent.getPayer().getFullName(), result.getName());
        assertEquals(bizEvent.getPayer().getEntityUniqueIdentifierValue(), result.getTaxCode());
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
        assertNotNull(result);
        User user = bizEvent.getTransactionDetails().getUser();
        String payerFullName = String.format("%s %s", user.getName(), user.getSurname());
        assertEquals(payerFullName, result.getName());
        assertEquals(bizEvent.getPayer().getEntityUniqueIdentifierValue(), result.getTaxCode());
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
        Long result = sut.getItemAmount(paymentInfo);
        assertEquals(100L, result);
    }

    @Test
    void getAmountNotPresent() {
        PaymentInfo paymentInfo = PaymentInfo.builder().build();
        Long result = sut.getItemAmount(paymentInfo);
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
}