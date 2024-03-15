package it.gov.pagopa.bizeventsdatastore.entity.view;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.OriginType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity model for biz-events-view-general
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class BizEventsViewGeneral {
    private String id;    
    private String transactionId;
    private String authCode;
    private PaymentMethodType paymentMethod;
    private String rrn;
    private String pspName;
    private String transactionDate;
    private WalletInfo walletInfo;
    private UserDetail payer;
    private boolean isCart;
    private String fee;
    private OriginType origin;
    private int totalNotice;

}
