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
public class Info {
	private String type;
	private String blurredNumber;
	private String holder;
	private String expireMonth;
	private String expireYear;
	private String brand;
	private String issuerAbi;
	private String issuerName;
	private String label;
}
