package it.gov.pagopa.bizeventsdatastore.entity;

import java.util.List;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.WalletType;
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
	private String idWallet;
	private WalletType walletType;
	private List<String> enableableFunctions;
	private boolean pagoPa;
	private String onboardingChannel;
	private boolean favourite;
	private String createDate;
	private Info info;
	private AuthRequest authRequest;
}
