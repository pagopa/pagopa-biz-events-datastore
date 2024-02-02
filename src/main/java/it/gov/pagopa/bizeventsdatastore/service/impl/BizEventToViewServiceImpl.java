package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.Creditor;
import it.gov.pagopa.bizeventsdatastore.entity.Debtor;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.Payer;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;
import it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.entity.Transfer;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.OriginType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.entity.view.UserDetail;
import it.gov.pagopa.bizeventsdatastore.entity.view.WalletInfo;
import it.gov.pagopa.bizeventsdatastore.exception.PDVTokenizerException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.PDVTokenizerServiceRetryWrapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@inheritDoc}
 */
@Slf4j
public class BizEventToViewServiceImpl implements BizEventToViewService {

    private final String[] unwantedRemittanceInfo = System.getenv().getOrDefault("UNWANTED_REMITTANCE_INFO", "pagamento multibeneficiario").split(",");

    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    private static final String MODEL_TYPE_IUV = "1";
    private static final String MODEL_TYPE_NOTICE = "2";
    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";
    private static final String RECEIPT_DATE_FORMAT = "dd MMMM yyyy, HH:mm:ss";

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerServiceRetry;

    public BizEventToViewServiceImpl() {
        this.pdvTokenizerServiceRetry = new PDVTokenizerServiceRetryWrapperImpl();
    }

    BizEventToViewServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerServiceRetry) {
        this.pdvTokenizerServiceRetry = pdvTokenizerServiceRetry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEventToViewResult mapBizEventToView(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        UserDetail debtor = getDebtor(bizEvent.getDebtor());
        UserDetail payer = getPayer(bizEvent);
        UserDetail tokenizedDebtor = null;
        UserDetail tokenizedPayer;
        boolean sameDebtorAndPayer = false;
        try {
            if (debtor != null && payer != null && debtor.getTaxCode() != null && debtor.getTaxCode().equals(payer.getTaxCode())) {
                tokenizedPayer = tokenizeUserDetail(payer);
                sameDebtorAndPayer = true;
            } else {
                tokenizedPayer = tokenizeUserDetail(payer);
                tokenizedDebtor = tokenizeUserDetail(debtor);
            }
        } catch (Exception e) {
            if (e instanceof PDVTokenizerException || e instanceof JsonProcessingException) {
                log.error("Error when tokenizing data for biz-even views. Biz-event id {}", bizEvent.getId(), e);
            }
            log.error("Unexpected error when processing user data for PDV tokenizer. Biz-event id {}", bizEvent.getId(), e);
            throw e;
        }

        if (tokenizedDebtor == null && tokenizedPayer == null) {
            return null;
        }

        List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
        if (tokenizedDebtor != null) {
            BizEventsViewUser debtorUserView = buildUserView(bizEvent, tokenizedDebtor, false);
            userViewToInsert.add(debtorUserView);
        }

        if (tokenizedPayer != null) {
            BizEventsViewUser payerUserView = buildUserView(bizEvent, tokenizedPayer, true);
            userViewToInsert.add(payerUserView);
        }

        return BizEventToViewResult.builder()
                .userViewList(userViewToInsert)
                .generalView(buildGeneralView(bizEvent, tokenizedPayer))
                .cartView(buildCartView(bizEvent, sameDebtorAndPayer ? tokenizedPayer : tokenizedDebtor))
                .build();
    }

    private UserDetail tokenizeUserDetail(UserDetail userDetail) throws PDVTokenizerException, JsonProcessingException {
        if (userDetail == null || userDetail.getTaxCode() == null) {
            return null;
        }
        String tokenizedFiscalCode = pdvTokenizerServiceRetry.generateTokenForFiscalCodeWithRetry(userDetail.getTaxCode());

        return UserDetail.builder()
                .name(userDetail.getName())
                .taxCode(tokenizedFiscalCode)
                .build();
    }

    UserDetail getDebtor(Debtor debtor) {
        if (debtor != null && isValidFiscalCode(debtor.getEntityUniqueIdentifierValue())) {
            return UserDetail.builder()
                    .name(debtor.getFullName())
                    .taxCode(debtor.getEntityUniqueIdentifierValue())
                    .build();
        }
        return null;
    }

    PaymentMethodType getPaymentMethod(PaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getPaymentMethod() != null && PaymentMethodType.isValidPaymentMethod(paymentInfo.getPaymentMethod())) {
            return PaymentMethodType.valueOf(paymentInfo.getPaymentMethod());
        }
        return PaymentMethodType.UNKNOWN;
    }

    OriginType getOrigin(TransactionDetails transactionDetails) {
        if (transactionDetails != null) {
            if (transactionDetails.getTransaction() != null
                    && transactionDetails.getTransaction().getOrigin() != null
                    && OriginType.isValidOrigin(transactionDetails.getTransaction().getOrigin())
            ) {
                return OriginType.valueOf(transactionDetails.getTransaction().getOrigin());
            }
            if (transactionDetails.getInfo() != null
                    && transactionDetails.getInfo().getClientId() != null
                    && OriginType.isValidOrigin(transactionDetails.getInfo().getClientId())
            ) {
                return OriginType.valueOf(transactionDetails.getInfo().getClientId());
            }
        }
        return OriginType.UNKNOWN;
    }

    String getTransactionId(BizEvent bizEvent) {
        PaymentInfo paymentInfo = bizEvent.getPaymentInfo();
        if (paymentInfo == null || paymentInfo.getTotalNotice() == null || "1".equals(paymentInfo.getTotalNotice())) {
            return bizEvent.getId();
        }
        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        if (transactionDetails != null && transactionDetails.getTransaction() != null && transactionDetails.getTransaction().getTransactionId() != null) {
            return transactionDetails.getTransaction().getTransactionId();
        }
        return null;
    }

    String getAuthCode(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getTransaction() != null && transactionDetails.getTransaction().getNumAut() != null) {
            return transactionDetails.getTransaction().getNumAut();
        }
        return null;
    }

    String getRrn(BizEvent bizEvent) {
        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        if (transactionDetails != null
                && transactionDetails.getTransaction() != null
                && transactionDetails.getTransaction().getRrn() != null
        ) {
            return transactionDetails.getTransaction().getRrn();
        }
        PaymentInfo paymentInfo = bizEvent.getPaymentInfo();
        if (paymentInfo != null) {
            if (paymentInfo.getPaymentToken() != null) {
                return paymentInfo.getPaymentToken();
            }
            if (paymentInfo.getIUR() != null) {
                return paymentInfo.getIUR();
            }
        }
        return null;
    }

    String getPspName(BizEvent bizEvent) {
        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        if (transactionDetails != null
                && transactionDetails.getTransaction() != null
                && transactionDetails.getTransaction().getPsp() != null
                && transactionDetails.getTransaction().getPsp().getBusinessName() != null) {
            return transactionDetails.getTransaction().getPsp().getBusinessName();
        }
        if (bizEvent.getPsp() != null && bizEvent.getPsp().getPsp() != null) {
            return bizEvent.getPsp().getPsp();
        }
        return null;
    }

    String getTransactionDate(BizEvent bizEvent) {
        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        if (transactionDetails != null &&
                transactionDetails.getTransaction() != null &&
                transactionDetails.getTransaction().getCreationDate() != null
        ) {
            return dateFormatZoned(transactionDetails.getTransaction().getCreationDate());
        }
        PaymentInfo paymentInfo = bizEvent.getPaymentInfo();
        if (paymentInfo != null && paymentInfo.getPaymentDateTime() != null) {
            return dateFormat(paymentInfo.getPaymentDateTime());
        }
        return null;
    }

    String getPaymentMethodAccountHolder(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getHolder();
        }
        return null;
    }

    String getBrand(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getBrand();
        }
        return null;
    }

    String getBlurredNumber(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getBlurredNumber();
        }
        return null;
    }

    String getFee(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getTransaction() != null && transactionDetails.getTransaction().getFee() != 0L) {
            return currencyFormat(String.valueOf(transactionDetails.getTransaction().getFee() / 100.00));
        }
        return null;
    }

    UserDetail getPayer(BizEvent bizEvent) {
        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        Payer payer = bizEvent.getPayer();

        if (transactionDetails == null || transactionDetails.getUser() == null) {
            if (payer != null && isValidFiscalCode(payer.getEntityUniqueIdentifierValue())) {
                return UserDetail.builder()
                        .name(payer.getFullName())
                        .taxCode(payer.getEntityUniqueIdentifierValue())
                        .build();
            }
            return null;
        }

        UserDetail userDetail = new UserDetail();
        if (isValidFiscalCode(transactionDetails.getUser().getFiscalCode())) {
            userDetail.setTaxCode(transactionDetails.getUser().getFiscalCode());
        } else if (payer != null && isValidFiscalCode(payer.getEntityUniqueIdentifierValue())) {
            userDetail.setTaxCode(payer.getEntityUniqueIdentifierValue());
        } else {
            return null;
        }

        if (transactionDetails.getUser().getName() != null && transactionDetails.getUser().getSurname() != null) {
            String fullName = String.format("%s %s", transactionDetails.getUser().getName(), transactionDetails.getUser().getSurname());
            userDetail.setName(fullName);
        } else if (payer != null && payer.getFullName() != null) {
            userDetail.setName(payer.getFullName());
        }

        return userDetail;
    }

    String getItemSubject(BizEvent bizEvent) {
        if (
                bizEvent.getPaymentInfo() != null &&
                        bizEvent.getPaymentInfo().getRemittanceInformation() != null &&
                        !Arrays.asList(unwantedRemittanceInfo).contains(bizEvent.getPaymentInfo().getRemittanceInformation())
        ) {
            return bizEvent.getPaymentInfo().getRemittanceInformation();
        }
        List<Transfer> transferList = bizEvent.getTransferList();
        if (transferList != null && !transferList.isEmpty()) {
            double amount = 0;
            String remittanceInformation = null;
            for (Transfer transfer : transferList) {
                double transferAmount;
                try {
                    transferAmount = Double.parseDouble(transfer.getAmount());
                } catch (Exception ignored) {
                    continue;
                }
                if (amount < transferAmount) {
                    amount = transferAmount;
                    remittanceInformation = transfer.getRemittanceInformation();
                }
            }
            return formatRemittanceInformation(remittanceInformation);
        }
        return null;
    }

    Long getItemAmount(PaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getAmount() != null) {
            return Long.parseLong(paymentInfo.getAmount());
        }
        return null;
    }

    UserDetail getPayee(Creditor creditor) {
        if (creditor != null) {
            return UserDetail.builder()
                    .name(creditor.getCompanyName())
                    .taxCode(creditor.getIdPA())
                    .build();
        }
        return null;
    }

    String getRefNumberType(DebtorPosition debtorPosition) {
        if (debtorPosition != null && debtorPosition.getModelType() != null) {
            if (debtorPosition.getModelType().equals(MODEL_TYPE_IUV)) {
                return REF_TYPE_IUV;
            }
            if (debtorPosition.getModelType().equals(MODEL_TYPE_NOTICE)) {
                return REF_TYPE_NOTICE;
            }
        }
        return null;
    }

    String getRefNumberValue(DebtorPosition debtorPosition) {
        if (debtorPosition != null && debtorPosition.getModelType() != null) {
            if (debtorPosition.getModelType().equals(MODEL_TYPE_IUV) && debtorPosition.getIuv() != null) {
                return debtorPosition.getIuv();
            }
            if (debtorPosition.getModelType().equals(MODEL_TYPE_NOTICE) && debtorPosition.getNoticeNumber() != null) {
                return debtorPosition.getNoticeNumber();
            }
        }
        return null;
    }

    int getTotalNotice(PaymentInfo paymentInfo) {
        if (paymentInfo == null || paymentInfo.getTotalNotice() == null) {
            return 1;
        }
        return Integer.parseInt(paymentInfo.getTotalNotice());
    }

    private BizEventsViewCart buildCartView(BizEvent bizEvent, UserDetail debtor) {
        return BizEventsViewCart.builder()
                .transactionId(getTransactionId(bizEvent))
                .eventId(bizEvent.getId())
                .subject(getItemSubject(bizEvent))
                .amount(getItemAmount(bizEvent.getPaymentInfo()))
                .debtor(debtor)
                .payee(getPayee(bizEvent.getCreditor()))
                .refNumberType(getRefNumberType(bizEvent.getDebtorPosition()))
                .refNumberValue(getRefNumberValue(bizEvent.getDebtorPosition()))
                .build();
    }

    private BizEventsViewGeneral buildGeneralView(BizEvent bizEvent, UserDetail payer) {
        return BizEventsViewGeneral.builder()
                .transactionId(getTransactionId(bizEvent))
                .authCode(getAuthCode(bizEvent.getTransactionDetails()))
                .rrn(getRrn(bizEvent))
                .transactionDate(getTransactionDate(bizEvent))
                .pspName(getPspName(bizEvent))
                .walletInfo(
                        WalletInfo.builder()
                                .accountHolder(getPaymentMethodAccountHolder(bizEvent.getTransactionDetails()))
                                .brand(getBrand(bizEvent.getTransactionDetails()))
                                .blurredNumber(getBlurredNumber(bizEvent.getTransactionDetails()))
                                .build())
                .payer(payer)
                .fee(getFee(bizEvent.getTransactionDetails()))
                .paymentMethod(getPaymentMethod(bizEvent.getPaymentInfo()))
                .origin(getOrigin(bizEvent.getTransactionDetails()))
                .totalNotice(getTotalNotice(bizEvent.getPaymentInfo()))
                .build();
    }

    private BizEventsViewUser buildUserView(BizEvent bizEvent, UserDetail userDetail, boolean isPayer) {
        return BizEventsViewUser.builder()
                .taxCode(userDetail.getTaxCode())
                .transactionId(getTransactionId(bizEvent))
                .transactionDate(getTransactionDate(bizEvent))
                .hidden(false)
                .isPayer(isPayer)
                .build();
    }

    private boolean isValidFiscalCode(String taxCode) {
        if (taxCode != null && !taxCode.isEmpty()) {
            Pattern patternCF = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
            Pattern patternPIVA = Pattern.compile("^\\d{11}");

            return patternCF.matcher(taxCode).find() || patternPIVA.matcher(taxCode).find();
        }
        return false;
    }

    private String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            Matcher matcher = pattern.matcher(remittanceInformation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    private String dateFormatZoned(String date) {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ofPattern(RECEIPT_DATE_FORMAT))
                .toFormatter(Locale.ITALY)
                .withZone(TimeZone.getTimeZone("Europe/Rome").toZoneId());
        try {
            return OffsetDateTime.parse(date).format(formatter);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private String dateFormat(String date) {
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern(RECEIPT_DATE_FORMAT).withLocale(Locale.ITALY);
        try {
            return LocalDateTime.parse(date).format(simpleDateFormat);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }
}