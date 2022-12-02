package it.gov.pagopa.bizeventsdatastore.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class MBD {
	@JsonProperty(value="IUBD")
	private String iubd;
	@JsonProperty(value="oraAcquisto")
	private String purchaseTime;
	@JsonProperty(value="importo")
	private String amount;
	@JsonProperty(value="tipoBollo")
	private String stampType;
	@JsonProperty(value="MBDAttachment")
	private String mbdAttachment; //MBD base64 

}
