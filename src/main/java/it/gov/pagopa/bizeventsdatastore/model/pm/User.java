package it.gov.pagopa.bizeventsdatastore.model.pm;

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
	private long userId;
	@Key
	private long userStatus;
	@Key
	private String userStatusDescription;
	@Key
	private String notificationEmail;
	@Key
	private UserType type;
	@Key
	private String fullName;
	@Key
	private String fiscalCode;
	@Key
	private String name;
	@Key
	private String surname;
}
