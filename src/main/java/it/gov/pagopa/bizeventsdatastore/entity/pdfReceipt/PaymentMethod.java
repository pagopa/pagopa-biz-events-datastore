package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod {
	private String name;
	private String logo;
	private String accountHolder;
	private String extraFee;
}
