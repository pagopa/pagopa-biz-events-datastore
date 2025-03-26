package it.gov.pagopa.bizeventsdatastore.util;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


public class BlobStorage {

    private final BlobServiceClient blobServiceClient;

    private static volatile BlobStorage instance;

    public static BlobStorage getInstance() {
        if (instance == null) {
            synchronized (BlobStorage.class) {
                if (instance == null) {
                    instance = new BlobStorage();
                }
            }
        }
        return instance;
    }

    private BlobStorage() {
        String connectionString = System.getenv("AzureWebJobsStorage");
        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    public boolean uploadToDeadLetter(String id, LocalDateTime now, String invocationId, String type, List<BizEvent> bizEvtMsg,
                                      String deadLetterContainerName) {
        // Create a directory structure (year/month/day/hour/session/<>)
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        String day = now.format(DateTimeFormatter.ofPattern("dd"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH"));
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String unixTime = String.valueOf(now.toEpochSecond(ZoneOffset.UTC));
        String session = String.format("%s-%s-%s", dateTime, unixTime, id.substring(0,8));
        String blobPath = String.format("%s/%s/%s/%s/%s/%s-%s.json", year, month, day,
                hour, session, session, type);
        try {
            blobServiceClient.createBlobContainerIfNotExists(deadLetterContainerName);
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(deadLetterContainerName);
            BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(blobPath).getBlockBlobClient();
            blockBlobClient.upload(BinaryData.fromObject(bizEvtMsg), true);
            blockBlobClient.setMetadata(Map.of("invocationId", invocationId));

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
