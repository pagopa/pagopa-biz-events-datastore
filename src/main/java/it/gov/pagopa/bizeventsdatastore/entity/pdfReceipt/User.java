package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
	private String mail;
	private Data data;
}
