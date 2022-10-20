package it.gov.pagopa.bizeventsdatastore.entity;

import com.azure.spring.data.cosmos.core.mapping.PartitionKey;

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
public class PaymentInfo {
	private String paymentDateTime;
	private String applicationDate;
	private String transferDate;
	private String dueDate;
	@PartitionKey
	private String paymentToken;
	private String amount;
	private String fee;
	private String primaryCiIncurredFee;
	private String idBundle;
	private String idCiBundle;
	private String totalNotice;
	private String paymentMethod;
	private String touchpoint;
	private String remittanceInformation;
}
