package it.gov.pagopa.bizeventsdatastore.entity;

import java.time.LocalDateTime;
import java.util.List;

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
	private List<String> missingInfo;
	private DebtorPosition debtorPosition;
	private Creditor creditor;
	private Psp psp;
	private Debtor debtor;
	private Payer payer;
	private PaymentInfo paymentInfo;
	private List<Transfer> transferList;
	private AdditionalPMInfo additionalPMInfo;

    private LocalDateTime insertedDate;
    private LocalDateTime lastUpdatedDate;
}
