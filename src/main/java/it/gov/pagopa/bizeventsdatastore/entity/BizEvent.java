package it.gov.pagopa.bizeventsdatastore.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import com.azure.spring.data.cosmos.core.mapping.Container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Container(containerName = "biz-events", autoCreateContainer = false, ru="1000", partitionKeyPath = "/creditor/idPA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizEvent {
	
	@Id
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

    @CreatedDate
    private LocalDateTime insertedDate;
    @LastModifiedDate
    private LocalDateTime lastUpdatedDate;
}
