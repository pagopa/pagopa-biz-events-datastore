package it.gov.pagopa.bizeventsdatastore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.util.Key;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.UserType;
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
public class User {
	
	@Key(value="idUser")
	@JsonProperty("idUser")
	private String userId;
	@Key
	private String userStatus;
	@Key
	private String userStatusDescription;
	@Key
	private String notificationEmail;
	@Key
	@Builder.Default
	private UserType type = UserType.F;
	@Key
	private String fullName;
	@Key
	private String fiscalCode;
}
