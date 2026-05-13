package it.gov.pagopa.bizeventsdatastore.service.impl;

import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventCosmosServiceImplTest {

    public static final String EVENT_ID = "1";
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @InjectMocks
    private BizEventCosmosServiceImpl sut;

    @ParameterizedTest
    @EnumSource(value = StatusType.class, names = {"DONE", "INGESTED"})
    void getBizEventSuccess(StatusType status) throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(BizEvent.builder().eventStatus(status).build());

        BizEvent result = assertDoesNotThrow(() -> sut.getBizEvent(EVENT_ID));

        assertNotNull(result);
    }

    @Test
    void getBizEventErrorNotFound() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenThrow(BizEventNotFoundException.class);

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEvent(EVENT_ID));
    }

    @ParameterizedTest
    @EnumSource(value = StatusType.class, names = {"DONE", "INGESTED"}, mode = EnumSource.Mode.EXCLUDE)
    void getBizEventErrorWrongStatus(StatusType status) throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(BizEvent.builder().eventStatus(status).build());

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEvent(EVENT_ID));
    }
}