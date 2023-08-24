package it.gov.pagopa.bizeventsdatastore.model;

import com.google.api.client.util.Key;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfoTransactionDetails {
	@Key
	private String brand;
	@Key
	private String brandLogo;
	@Key
	private String clientId;
	@Key
	private String paymentMethodName;
	@Key
	private String type;
}