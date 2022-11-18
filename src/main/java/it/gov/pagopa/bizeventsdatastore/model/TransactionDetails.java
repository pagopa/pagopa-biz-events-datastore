package it.gov.pagopa.bizeventsdatastore.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDetails {
	private User user;
	private PaymentAuthorizationRequest paymentAuthorizationRequest;
	private WalletItem wallet;
}
