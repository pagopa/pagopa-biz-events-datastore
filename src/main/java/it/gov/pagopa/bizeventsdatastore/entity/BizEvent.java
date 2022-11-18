package it.gov.pagopa.bizeventsdatastore.entity;

import java.util.List;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
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
public class BizEvent {
	private String id;
	private String version;
	private String idPaymentManager;
	private String complete;
	private String receiptId;
	private List<String> missingInfo;
	private DebtorPosition debtorPosition;
	private Creditor creditor;
	private Psp psp;
	private Debtor debtor;
	private Payer payer;
	private PaymentInfo paymentInfo;
	private List<Transfer> transferList;
	private TransactionDetails transactionDetails;
	
	// internal status field
	@Builder.Default
	private StatusType eventStatus = StatusType.NA;
}
