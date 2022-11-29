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
public class Info {
	@Key
	private String type;
	@Key
	private String blurredNumber;
	@Key
	private String holder;
	@Key
	private String expireMonth;
	@Key
	private String expireYear;
	@Key
	private String brand;
	@Key
	private String issuerAbi;
	@Key
	private String issuerName;
	@Key
	private String label;
}
