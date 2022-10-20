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
public class DebtorPosition {
	private String modelType;
	private String noticeNumber;
	@PartitionKey
	private String iuv;
}
