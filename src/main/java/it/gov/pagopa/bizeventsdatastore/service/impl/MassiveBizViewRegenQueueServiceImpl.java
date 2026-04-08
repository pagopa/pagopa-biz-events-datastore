package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.bizeventsdatastore.client.MassiveBizViewRegenQueueClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.MassiveBizViewRegenQueueClientImpl;
import it.gov.pagopa.bizeventsdatastore.service.MassiveBizViewRegenQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@inheritDoc}
 */
public class MassiveBizViewRegenQueueServiceImpl implements MassiveBizViewRegenQueueService {

    private final Logger logger = LoggerFactory.getLogger(MassiveBizViewRegenQueueServiceImpl.class);

    private final MassiveBizViewRegenQueueClient massiveBizViewRegenQueueClient;

    MassiveBizViewRegenQueueServiceImpl(MassiveBizViewRegenQueueClient massiveBizViewRegenQueueClient) {
        this.massiveBizViewRegenQueueClient = massiveBizViewRegenQueueClient;
    }

    public MassiveBizViewRegenQueueServiceImpl() {
        this.massiveBizViewRegenQueueClient = MassiveBizViewRegenQueueClientImpl.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendBizEventIdToQueue(String bizEventId) {
        try {
            Response<SendMessageResult> response = this.massiveBizViewRegenQueueClient.sendMessageToQueue(bizEventId);
            if (response.getStatusCode() == HttpStatus.CREATED.value()) {
                return true;
            }
            logger.error("Sending BizEvent id {} to queue failed. Response status {}", bizEventId, response.getStatusCode());
        } catch (Exception e) {
            logger.error("Unexpected error while sending BizEvent id {} to queue", bizEventId, e);
        }
        return false;
    }
}
