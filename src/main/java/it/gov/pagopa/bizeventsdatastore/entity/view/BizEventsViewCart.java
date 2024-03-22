package it.gov.pagopa.bizeventsdatastore.entity.view;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity model for biz-events-view-cart
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class BizEventsViewCart {
	@NotBlank
	private String id;
	@NotBlank
    private String transactionId;
	@NotBlank
    private String eventId;
	@NotBlank
    private String subject;
	@NotBlank
    private String amount;
	@Valid
    private UserDetail payee;
	@Valid
    private UserDetail debtor;
    @NotBlank
    private String refNumberValue;
    @NotBlank
    private String refNumberType;
}
