package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {
	private String subject;
	private String amount;
	private RefNumber refNumber;
	private Debtor debtor;
	private Payee payee;
}
