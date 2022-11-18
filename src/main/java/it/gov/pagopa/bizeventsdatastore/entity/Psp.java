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
public class Psp {
	private String idPsp;
	private String idBrokerPsp;
	private String idChannel;
	@JsonProperty(value="psp")
	private String pspCompanyName;
	private String pspPartitaIVA;
	private String pspFiscalCode;
	private String channelDescription;
}
