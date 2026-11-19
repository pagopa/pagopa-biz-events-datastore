package it.gov.pagopa.bizeventsdatastore.util;

import com.azure.core.util.BinaryData;
import com.azure.core.util.serializer.TypeReference;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.model.DeadLetterBlob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;


public class BlobStorage {

    private static final DateTimeFormatter MONTHLY_PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/");

    private final Logger logger = LoggerFactory.getLogger(BlobStorage.class);

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

    BlobStorage(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
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
            logger.error("Unable to upload the blob [{}] to the dead letter container [{}] with invocationId [{}]",
                    blobPath, deadLetterContainerName, invocationId, e);
            return false;
        }
    }

    /**
     * Lists the blobs of a dead letter container that were created from the given date and match the given filter.
     * Only the monthly folders (yyyy/MM/) of the dead letter path from the given date are listed, so that the older blobs
     * are never enumerated: a blob saved outside these folders is not listed.
     *
     * @param deadLetterContainerName the name of the dead letter container
     * @param createdFrom             the blobs created before this date are ignored
     * @param filter                  the filter to apply to the blobs
     * @param maxResults              the maximum number of blobs to return
     * @return the matching blobs, empty if the container does not exist
     */
    public List<DeadLetterBlob> listDeadLetterBlobs(String deadLetterContainerName, OffsetDateTime createdFrom,
                                                    Predicate<DeadLetterBlob> filter, int maxResults) {
        return listDeadLetterBlobs(deadLetterContainerName, createdFrom, OffsetDateTime.now(ZoneOffset.UTC), filter, maxResults);
    }

    List<DeadLetterBlob> listDeadLetterBlobs(String deadLetterContainerName, OffsetDateTime createdFrom, OffsetDateTime now,
                                             Predicate<DeadLetterBlob> filter, int maxResults) {
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(deadLetterContainerName);
        if (!blobContainerClient.exists()) {
            return List.of();
        }
        return monthlyPrefixes(createdFrom, now).stream()
                .flatMap(prefix -> blobContainerClient.listBlobs(new ListBlobsOptions()
                        .setPrefix(prefix)
                        .setDetails(new BlobListDetails().setRetrieveMetadata(true)), null).stream())
                .filter(blobItem -> !blobItem.getProperties().getCreationTime().isBefore(createdFrom))
                .map(this::toDeadLetterBlob)
                .filter(filter)
                .limit(maxResults)
                .toList();
    }

    /**
     * The folders of the dead letter path use the local date of the function (see uploadToDeadLetter):
     * a day of margin is added on both sides to be independent of the time zone.
     */
    static List<String> monthlyPrefixes(OffsetDateTime from, OffsetDateTime to) {
        YearMonth month = YearMonth.from(from.withOffsetSameInstant(ZoneOffset.UTC).minusDays(1));
        YearMonth lastMonth = YearMonth.from(to.withOffsetSameInstant(ZoneOffset.UTC).plusDays(1));
        List<String> prefixes = new ArrayList<>();
        while (!month.isAfter(lastMonth)) {
            prefixes.add(month.format(MONTHLY_PREFIX_FORMAT));
            month = month.plusMonths(1);
        }
        return prefixes;
    }

    /**
     * Reads the biz-events saved in a dead letter blob.
     *
     * @param deadLetterContainerName the name of the dead letter container
     * @param blobName                the name of the blob
     * @return the saved biz-events
     */
    public List<BizEvent> readDeadLetterBlob(String deadLetterContainerName, String blobName) {
        return getBlobClient(deadLetterContainerName, blobName).downloadContent().toObject(new TypeReference<List<BizEvent>>() {});
    }

    /**
     * Deletes a dead letter blob.
     *
     * @param deadLetterContainerName the name of the dead letter container
     * @param blobName                the name of the blob
     */
    public void deleteDeadLetterBlob(String deadLetterContainerName, String blobName) {
        getBlobClient(deadLetterContainerName, blobName).delete();
    }

    /**
     * Replaces the metadata of a dead letter blob.
     *
     * @param deadLetterContainerName the name of the dead letter container
     * @param blobName                the name of the blob
     * @param metadata                the new metadata
     */
    public void updateDeadLetterBlobMetadata(String deadLetterContainerName, String blobName, Map<String, String> metadata) {
        getBlobClient(deadLetterContainerName, blobName).setMetadata(metadata);
    }

    private BlobClient getBlobClient(String deadLetterContainerName, String blobName) {
        return blobServiceClient.getBlobContainerClient(deadLetterContainerName).getBlobClient(blobName);
    }

    private DeadLetterBlob toDeadLetterBlob(BlobItem blobItem) {
        Map<String, String> metadata = blobItem.getMetadata() == null ? new HashMap<>() : new HashMap<>(blobItem.getMetadata());
        return new DeadLetterBlob(blobItem.getName(), metadata);
    }
}
