package it.gov.pagopa.bizeventsdatastore.util;

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
import it.gov.pagopa.bizeventsdatastore.entity.view.UserDetail;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizEventToViewUtils {
    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    public static final String MODEL_TYPE_IUV = "1";
    public static final String MODEL_TYPE_NOTICE = "2";
    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";
    private static final String RECEIPT_DATE_FORMAT = "dd MMMM yyyy, HH:mm:ss";

    private static final String[] UNWANTED_REMITTANCE_INFO = System.getenv().getOrDefault("UNWANTED_REMITTANCE_INFO", "pagamento multibeneficiario").split(",");

    private BizEventToViewUtils() {
    }

    public static UserDetail getDebtor(Debtor debtor) {
        if (debtor != null && debtor.getEntityUniqueIdentifierValue() != null) {
            return UserDetail.builder()
                    .name(debtor.getFullName())
                    .taxCode(debtor.getEntityUniqueIdentifierValue())
                    .build();
        }
        return null;
    }

    public static PaymentMethodType getPaymentMethod(PaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getPaymentMethod() != null && PaymentMethodType.isValidPaymentMethod(paymentInfo.getPaymentMethod())) {
            return PaymentMethodType.valueOf(paymentInfo.getPaymentMethod());
        }
        return PaymentMethodType.UNKNOWN;
    }

    public static OriginType getOrigin(TransactionDetails transactionDetails) {
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

    public static String getTransactionId(BizEvent bizEvent) {
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

    public static String getAuthCode(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getTransaction() != null && transactionDetails.getTransaction().getNumAut() != null) {
            return transactionDetails.getTransaction().getNumAut();
        }
        return null;
    }

    public static String getRrn(BizEvent bizEvent) {
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

    public static String getPspName(BizEvent bizEvent) {
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

    public static String getTransactionDate(BizEvent bizEvent) {
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

    public static String getPaymentMethodAccountHolder(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getHolder();
        }
        return null;
    }

    public static String getBrand(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getBrand();
        }
        return null;
    }

    public static String getBlurredNumber(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getWallet() != null && transactionDetails.getWallet().getInfo() != null) {
            return transactionDetails.getWallet().getInfo().getBlurredNumber();
        }
        return null;
    }

    public static String getFee(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getTransaction() != null && transactionDetails.getTransaction().getFee() != 0L) {
            return currencyFormat(String.valueOf(transactionDetails.getTransaction().getFee() / 100.00));
        }
        return null;
    }

    public static UserDetail getPayer(BizEvent bizEvent) {
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
        if (transactionDetails.getUser().getFiscalCode() != null && isValidFiscalCode(transactionDetails.getUser().getFiscalCode())) {
            userDetail.setTaxCode(transactionDetails.getUser().getFiscalCode());
        } else if (payer.getEntityUniqueIdentifierValue() != null && isValidFiscalCode(payer.getEntityUniqueIdentifierValue())) {
            userDetail.setTaxCode(transactionDetails.getUser().getFiscalCode());
        } else {
            return null;
        }

        if (transactionDetails.getUser().getName() != null && transactionDetails.getUser().getSurname() != null) {
            String fullName = String.format("%s %s", transactionDetails.getUser().getName(), transactionDetails.getUser().getSurname());
            userDetail.setTaxCode(fullName);
        } else if (payer.getFullName() != null) {
            userDetail.setTaxCode(payer.getFullName());
        }

        return userDetail;
    }

    public static String getItemSubject(BizEvent bizEvent) {
        if (
                bizEvent.getPaymentInfo() != null &&
                        bizEvent.getPaymentInfo().getRemittanceInformation() != null &&
                        !Arrays.asList(UNWANTED_REMITTANCE_INFO).contains(bizEvent.getPaymentInfo().getRemittanceInformation())
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

    public static Long getItemAmount(PaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getAmount() != null) {
            return Long.parseLong(paymentInfo.getAmount());
        }
        return null;
    }

    public static UserDetail getPayee(Creditor creditor) {
        if (creditor != null) {
            return UserDetail.builder()
                    .name(creditor.getCompanyName())
                    .taxCode(creditor.getIdPA())
                    .build();
        }
        return null;
    }

    public static String getRefNumberType(DebtorPosition debtorPosition) {
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

    public static String getRefNumberValue(DebtorPosition debtorPosition) {
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

    public static boolean isValidFiscalCode(String taxCode) {
        if (taxCode != null && !taxCode.isEmpty()) {
            Pattern patternCF = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
            Pattern patternPIVA = Pattern.compile("/^[0-9]{11}$/");

            return patternCF.matcher(taxCode).find() || patternPIVA.matcher(taxCode).find();
        }
        return false;
    }

    public static int getTotalNotice(PaymentInfo paymentInfo) {
        if (paymentInfo == null || paymentInfo.getTotalNotice() == null || "1".equals(paymentInfo.getTotalNotice())) {
            return 1;
        }
        return Integer.parseInt(paymentInfo.getTotalNotice());
    }

    private static String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            Matcher matcher = pattern.matcher(remittanceInformation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    private static String dateFormatZoned(String date) {
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

    private static String dateFormat(String date) {
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern(RECEIPT_DATE_FORMAT).withLocale(Locale.ITALY);
        try {
            return LocalDateTime.parse(date).format(simpleDateFormat);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }
}
