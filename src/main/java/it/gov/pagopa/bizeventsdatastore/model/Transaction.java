package it.gov.pagopa.bizeventsdatastore.model;

import com.google.api.client.util.Key;

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
	@Key
	private long transactionId;
	@Key
	private String grandTotal;
	@Key
	private String amount;
	@Key
	private String fee;
	@Key
	private String transactionStatus;
	@Key
	private String accountingStatus;
	@Key
	private String rrn;
	@Key
	private String authorizationCode;
	@Key
	private String creationDate;
	@Key
	private String numAut;
	@Key
	private String accountCode;
	@Key
	private Psp psp;
	@Key
	private String timestampOperation;
	@Key
	private String paymentGateway;
	
}
