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
public class PaymentAuthorizationRequest {
	@Key
	private String authOutcome;
	@Key
	private String requestId;
	@Key
	private String correlationId;
	@Key
	private String authCode;
	@Key
	private String paymentMethodType;
	@Key
	private Details details;
}
