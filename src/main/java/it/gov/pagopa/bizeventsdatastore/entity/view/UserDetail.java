package it.gov.pagopa.bizeventsdatastore.entity.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetail implements Serializable {

    private String name;
    private String taxCode;
}
