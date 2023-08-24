package it.gov.pagopa.bizeventsdatastore.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfoTransactionDetails {
	private String brand;
	private String brandLogo;
	private String clientId;
	private String paymentMethod;
	private String type;
}
