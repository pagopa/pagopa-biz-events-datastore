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
public class Details {
	@Key
	private String blurredNumber;
	@Key
	private String holder;
	@Key
	private String circuit; 
}
