package it.gov.pagopa.bizeventsdatastore.entity.view;

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
    private String taxCode;
    private String transactionId;
    private String transactionDate;
    private boolean hidden;
    private boolean isPayer;
}
