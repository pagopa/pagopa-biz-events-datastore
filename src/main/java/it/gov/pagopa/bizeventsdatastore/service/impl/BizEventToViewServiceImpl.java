package it.gov.pagopa.bizeventsdatastore.service.impl;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.Creditor;
import it.gov.pagopa.bizeventsdatastore.entity.Debtor;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;
import it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.entity.Transfer;
import it.gov.pagopa.bizeventsdatastore.entity.User;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.ServiceIdentifierType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.UserType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.entity.view.UserDetail;
import it.gov.pagopa.bizeventsdatastore.entity.view.WalletInfo;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.BizEventsViewValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@inheritDoc}
 */
public class BizEventToViewServiceImpl implements BizEventToViewService {

    private final String[] unwantedRemittanceInfo = System.getenv().getOrDefault("UNWANTED_REMITTANCE_INFO", "pagamento multibeneficiario").split(",");
    private final List<String> authenticatedChannels = Arrays.asList(System.getenv().getOrDefault("AUTHENTICATED_CHANNELS", "IO").split(","));

    private static final List<String> ECOMMERCE = Arrays.asList("CHECKOUT", "CHECKOUT_CART");
    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    private static final String MODEL_TYPE_IUV = "1";
    private static final String MODEL_TYPE_NOTICE = "2";
    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";
    private static final String SERVICE_IDENTIFIER_KEY = "serviceIdentifier";


    /**
     * {@inheritDoc}
     * @throws BizEventToViewConstraintViolationException
     */
    @Override
    public BizEventToViewResult mapBizEventToView(Logger logger, BizEvent bizEvent) throws BizEventToViewConstraintViolationException {
    	UserDetail debtor = getDebtor(bizEvent.getDebtor());
    	UserDetail payer = getPayer(bizEvent);
    	boolean sameDebtorAndPayer = false;
    	
    	if (debtor == null && payer == null) {
    		return null;
    	}

    	if (debtor != null && payer != null && debtor.getTaxCode() != null && debtor.getTaxCode().equals(payer.getTaxCode())) {
    		sameDebtorAndPayer = true;
    		// only the payer user is created when payer and debtor are the same
    		debtor = null;
    	} 

    	List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
    	
    	if (debtor != null) {
    		BizEventsViewUser debtorUserView = buildUserView(bizEvent, debtor, false, true);
    		userViewToInsert.add(debtorUserView);
    	}

    	if (payer != null) {
    		BizEventsViewUser payerUserView = buildUserView(bizEvent, payer, true, sameDebtorAndPayer);
    		userViewToInsert.add(payerUserView);
    	}

    	BizEventToViewResult result = BizEventToViewResult.builder()
    			.userViewList(userViewToInsert)
    			.generalView(buildGeneralView(bizEvent, payer))
    			.cartView(buildCartView(bizEvent, sameDebtorAndPayer ? payer : debtor))
    			.build();


    	BizEventsViewValidator.validate(logger, result, bizEvent);

    	return result;
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

    boolean isValidChannelOrigin(BizEvent bizEvent) {
        var details = bizEvent.getTransactionDetails();
        if (details == null) {
            return false;
        }

        String origin = details.getTransaction() != null
                ? details.getTransaction().getOrigin()
                : null;

        String clientId = details.getInfo() != null
                ? details.getInfo().getClientId()
                : null;

        UserType userType = details.getUser() != null
                ? details.getUser().getType()
                : null;

        boolean isAuthenticated = authenticatedChannels.contains(origin) || authenticatedChannels.contains(clientId);
        boolean isCheckout = ECOMMERCE.contains(origin) || ECOMMERCE.contains(clientId);
        boolean isRegisteredUser = UserType.REGISTERED.equals(userType);

        if (isCheckout && !isRegisteredUser) {
            return false;
        }

        return isAuthenticated;
    }
    
    ServiceIdentifierType getServiceIdentifier(Map<String, Object> properties) {
        if (properties != null && 
        		properties.get(SERVICE_IDENTIFIER_KEY) != null &&  
        		ServiceIdentifierType.isValidServiceIdentifier(properties.get(SERVICE_IDENTIFIER_KEY).toString())) {
        		return ServiceIdentifierType.valueOf(properties.get(SERVICE_IDENTIFIER_KEY).toString());
        }
        return ServiceIdentifierType.UNKNOWN;
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
            return transactionDetails.getTransaction().getCreationDate();
        }
        PaymentInfo paymentInfo = bizEvent.getPaymentInfo();
        if (paymentInfo != null && paymentInfo.getPaymentDateTime() != null) {
            return paymentInfo.getPaymentDateTime();
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
    
    String getMaskedEmail(TransactionDetails transactionDetails) {
        if (transactionDetails != null && transactionDetails.getInfo() != null) {
            return transactionDetails.getInfo().getMaskedEmail();
        }
        return null;
    }

    UserDetail getPayer(BizEvent bizEvent) {
    	
    	TransactionDetails transactionDetails = bizEvent.getTransactionDetails();

        // Verify that TransactionDetails and User are not null
        if (transactionDetails == null || transactionDetails.getUser() == null) {
            return null;
        }

        User user = transactionDetails.getUser();

        // Check if the user's tax code is valid
        if (!isValidFiscalCode(user.getFiscalCode())) {
            return null;
        }

        UserDetail userDetail = new UserDetail();
        userDetail.setTaxCode(user.getFiscalCode());

        // Set the full name if both first and last names are available
        if (user.getName() != null && user.getSurname() != null) {
            userDetail.setName(user.getName() + " " + user.getSurname());
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

    String getItemAmount(PaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getAmount() != null) {
            return paymentInfo.getAmount();
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
    
    boolean getIsCart(PaymentInfo paymentInfo) {
    	return paymentInfo != null && paymentInfo.getTotalNotice() != null && Integer.parseInt(paymentInfo.getTotalNotice()) > 1;
    }

    private BizEventsViewCart buildCartView(BizEvent bizEvent, UserDetail debtor) {
        return BizEventsViewCart.builder()
        		.id(bizEvent.getId())
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
        		.id(bizEvent.getId())
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
                                .maskedEmail(getMaskedEmail(bizEvent.getTransactionDetails()))
                                .build())
                .payer(payer)
                .fee(getFee(bizEvent.getTransactionDetails()))
                .paymentMethod(getPaymentMethod(bizEvent.getPaymentInfo()))
                .origin(getServiceIdentifier(bizEvent.getProperties()))
                .totalNotice(getTotalNotice(bizEvent.getPaymentInfo()))
                .isCart(getIsCart(bizEvent.getPaymentInfo()))
                .build();
    }

    private BizEventsViewUser buildUserView(BizEvent bizEvent, UserDetail userDetail, boolean isPayer, boolean isDebtor) {
        /*
         * The view user is hidden if:
         * - the event IS CartMod1
         *   OR
         * - the user is NOT a debtor AND
         *     (the user is NOT a payer OR the channel is NOT valid)
         */
        boolean isValidChannel = isValidChannelOrigin(bizEvent);

        boolean isHidden = isCartMod1(bizEvent) || (!isDebtor && !(isPayer && isValidChannel));

        return BizEventsViewUser.builder()
        		.id(bizEvent.getId()+(isPayer?"-p":"-d"))
                .taxCode(userDetail.getTaxCode())
                .transactionId(getTransactionId(bizEvent))
                .transactionDate(getTransactionDate(bizEvent))
                .hidden(isHidden)
                .isPayer(isPayer)
                .isDebtor(isDebtor)
                .build();
    }

    private boolean isValidFiscalCode(String taxCode) {
        if (taxCode != null && !taxCode.isEmpty()) {
            Pattern patternCF = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
            Pattern patternPIVA = Pattern.compile("^\\d{11}");

            return patternCF.matcher(taxCode.toUpperCase()).find() || patternPIVA.matcher(taxCode).find();
        }
        return false;
    }
    
    private String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            // replaceAll with '\R' to remove any unicode linebreak sequence
            Matcher matcher = pattern.matcher(remittanceInformation.replaceAll("\\R", ""));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    private String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }
    
    /**
     * Method to check if the content comes from a legacy cart model (see https://pagopa.atlassian.net/browse/VAS-1167)
     * @param bizEvent bizEvent to validate
     * @return true if it is considered a legacy cart content (not having a totalNotice field and having amount values != 0)
     * false otherwise
     *
     */
    private boolean isCartMod1(BizEvent bizEvent) {
        if (bizEvent.getPaymentInfo() != null
                && bizEvent.getPaymentInfo().getTotalNotice() == null) {

            if (bizEvent.getTransactionDetails() == null) {
                return true;
            }

            BigDecimal paymentAmount =
                    new BigDecimal(bizEvent.getPaymentInfo().getAmount());

            BigDecimal transactionAmount =
                    formatEuroCentAmount(
                            bizEvent.getTransactionDetails()
                                    .getTransaction()
                                    .getAmount());

            return paymentAmount.subtract(transactionAmount)
                    .compareTo(BigDecimal.ZERO) != 0;
        }

        return false;
    }
    
    private BigDecimal formatEuroCentAmount(long value) {
        BigDecimal amount = new BigDecimal(value);
        BigDecimal divider = new BigDecimal(100);
        return amount.divide(divider, 2, RoundingMode.UNNECESSARY);
    }
}
