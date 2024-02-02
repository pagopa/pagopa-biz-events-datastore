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
public class WalletInfo implements Serializable {

    private String accountHolder;
    private String brand;
    private String blurredNumber;
}
