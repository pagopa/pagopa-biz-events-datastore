package it.gov.pagopa.bizeventsdatastore.model;

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
public class Details {
	private String blurredNumber;
	private String holder;
	private String circuit; 
}
