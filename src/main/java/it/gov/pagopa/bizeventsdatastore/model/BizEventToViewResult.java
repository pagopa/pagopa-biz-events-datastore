package it.gov.pagopa.bizeventsdatastore.model;

import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Model that hold the result of the BizEvent to view mapping
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class BizEventToViewResult {

    private List<BizEventsViewUser> userViewList;
    private BizEventsViewGeneral generalView;
    private BizEventsViewCart cartView;
}
