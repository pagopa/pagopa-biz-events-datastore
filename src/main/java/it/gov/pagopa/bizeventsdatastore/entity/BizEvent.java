package it.gov.pagopa.bizeventsdatastore.entity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

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
	@Builder.Default
	private Long timestamp = ZonedDateTime.now().toInstant().toEpochMilli();
	private Map<String, Object> properties;
	
	// internal management field
	@Builder.Default
	private StatusType eventStatus = StatusType.NA;
	@Builder.Default
	private Integer eventRetryEnrichmentCount = 0;
	@Builder.Default
	private Boolean eventTriggeredBySchedule = Boolean.FALSE;
	private String eventErrorMessage;
	
}
