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
public class Transfer {
	private String fiscalCodePA;
	private String companyName;
	private String amount;
	private String transferCategory;
	private String remittanceInformation;
}
