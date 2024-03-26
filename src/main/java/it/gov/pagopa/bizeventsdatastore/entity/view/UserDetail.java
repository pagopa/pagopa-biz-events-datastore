package it.gov.pagopa.bizeventsdatastore.entity.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetail implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = -356906385523309670L;
	private String name;
    @NotBlank
    private String taxCode;
}
