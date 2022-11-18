package it.gov.pagopa.bizeventsdatastore.model;

import com.fasterxml.jackson.annotation.JsonProperty;

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
	@JsonProperty(value="idUser")
	private String userId;
	private String userStatus;
	private String userStatusDescription;
	private String notificationEmail;
	private UserType type;
	private String fullName;
	private String fiscalCode;
}
