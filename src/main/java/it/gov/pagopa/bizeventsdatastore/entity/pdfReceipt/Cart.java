package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {
	private String amountPartial;
	private Item item;
}
