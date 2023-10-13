package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.RefNumberType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefNumber {
	private String value;
	private RefNumberType type;

}
