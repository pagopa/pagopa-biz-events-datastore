package it.gov.pagopa.bizeventsdatastore.entity.view;

import jakarta.validation.constraints.NotBlank;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.ServiceIdentifierType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.PaymentMethodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity model for biz-events-view-general
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BizEventsViewGeneral {
	@NotBlank
	private String id;
	@NotBlank
    private String transactionId;
    private String authCode;
    private PaymentMethodType paymentMethod;
    @NotBlank
    private String rrn;
    @NotBlank
    private String pspName;
    @NotBlank
    private String transactionDate;
    private WalletInfo walletInfo;
    private UserDetail payer;
    private boolean isCart;
    private String fee;
    private ServiceIdentifierType origin;
    private int totalNotice;

}
