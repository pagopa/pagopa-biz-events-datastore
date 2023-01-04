package it.gov.pagopa.bizeventsdatastore.model;

import java.util.List;

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
public class WalletItem {
	@Key
	private long idWallet;
	@Key
	private String walletType;
	@Key
	private List<String> enableableFunctions;
	@Key
	private boolean pagoPa;
	@Key
	private String onboardingChannel;
	@Key
	private boolean favourite;
	@Key
	private String createDate;
	@Key
	private Info info;
}
