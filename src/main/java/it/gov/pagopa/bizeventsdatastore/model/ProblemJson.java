package it.gov.pagopa.bizeventsdatastore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Object returned as response in case of an error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemJson {

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    @Min(100)
    @Max(600)
    private Integer status;

    @JsonProperty("detail")
    private String detail;

}