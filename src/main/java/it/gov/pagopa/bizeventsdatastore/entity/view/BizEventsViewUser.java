package it.gov.pagopa.bizeventsdatastore.entity.view;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity model for biz-events-view-user
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class BizEventsViewUser {
	@NotBlank
	private String id;
	@NotBlank
    private String taxCode;
    @NotBlank
    private String transactionId;
    @NotBlank
    private String transactionDate;
    private boolean hidden;
    @JsonProperty("isPayer")
    @Getter(onMethod_ = @JsonProperty("isPayer"))
    private boolean isPayer;
    @JsonProperty("isDebtor")
    @Getter(onMethod_ = @JsonProperty("isDebtor"))
    private boolean isDebtor;
}
