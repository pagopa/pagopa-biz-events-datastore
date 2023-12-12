package it.gov.pagopa.bizeventsdatastore.model;


import com.google.api.client.util.Key;

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
	@Builder.Default
	private String origin = "PaymentManager";
	@Key
	private User user;
	@Key
	private Transaction transaction;
	@Key
	private WalletItem wallet;
	@Key
	private InfoECommerce info;
}
