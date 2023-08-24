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
public class Transaction {
	private long transactionId;
	private String grandTotal;
	private String amount;
	private String fee;
	private String transactionStatus;
	private String accountingStatus;
	private String rrn;
	private String authorizationCode;
	private String creationDate;
	private String numAut;
	private String accountCode;
	private TransactionPsp psp;
	private String timestampOperation;
	private String paymentGateway;
}
