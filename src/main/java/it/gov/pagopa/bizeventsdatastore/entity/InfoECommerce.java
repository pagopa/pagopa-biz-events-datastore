package it.gov.pagopa.bizeventsdatastore.entity;

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
	private String brand;
	private String brandLogo;
	private String clientId;
	private String paymentMethodName;
	private String type;
}
