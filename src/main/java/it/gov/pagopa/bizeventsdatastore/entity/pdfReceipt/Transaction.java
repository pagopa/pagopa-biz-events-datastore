package it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
	private long id;
	private String timestamp;
	private String amount;
	private boolean requestedByDebtor;
	private String rrn;
	private String authCode;
	private String numAut;
	private String accountCode;
	private Psp psp;
	private PaymentMethod paymentMethod;
	private User user;
}
