package it.gov.pagopa.bizeventsdatastore.model.pm;

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
public class InfoECommerce {
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
