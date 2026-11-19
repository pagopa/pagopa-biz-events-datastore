package it.gov.pagopa.bizeventsdatastore.util;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.google.gson.Gson;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.model.DeadLetterBlob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlobStorageTest {

    private static final String CONTAINER = "container";
    private static final String BLOB = "2026/11/06/blob.json";
    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 11, 6, 0, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private BlobServiceClient blobServiceClient;
    @Mock
    private BlobContainerClient blobContainerClient;
    @Mock
    private BlobClient blobClient;
    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    private BlobStorage sut;

    @BeforeEach
    void setUp() {
        lenient().when(blobServiceClient.getBlobContainerClient(CONTAINER)).thenReturn(blobContainerClient);
        lenient().when(blobContainerClient.getBlobClient(BLOB)).thenReturn(blobClient);
        sut = new BlobStorage(blobServiceClient);
    }

    @Test
    void realBlobClientCanBeBuiltWithTheResolvedDependencies() {
        String connectionString = "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=YWJjZGVmZ2g=;EndpointSuffix=core.windows.net";

        assertDoesNotThrow(() -> new BlobServiceClientBuilder().connectionString(connectionString).buildClient()
                .getBlobContainerClient(CONTAINER).getBlobClient(BLOB).getBlockBlobClient());
    }

    @Test
    void listDeadLetterBlobsContainerNotFound() {
        when(blobContainerClient.exists()).thenReturn(false);

        assertTrue(sut.listDeadLetterBlobs(CONTAINER, FROM, blob -> true, 10).isEmpty());
    }

    @Test
    void listDeadLetterBlobsAppliesDateFilterAndLimit() {
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobContainerClient.listBlobs(any(ListBlobsOptions.class), isNull())).thenReturn(pagedIterable);
        when(pagedIterable.stream()).thenReturn(Stream.of(
                blobItem("old", FROM.minusSeconds(1), null),
                blobItem("no-metadata", FROM, null),
                blobItem("excluded", FROM.plusDays(1), Map.of("skip", "true")),
                blobItem("valid", FROM.plusDays(2), Map.of("invocationId", "1")),
                blobItem("over-limit", FROM.plusDays(3), Map.of())));

        List<DeadLetterBlob> result = sut.listDeadLetterBlobs(CONTAINER, FROM, FROM.plusDays(10),
                blob -> !blob.getMetadata().containsKey("skip"), 2);

        assertEquals(List.of("no-metadata", "valid"), result.stream().map(DeadLetterBlob::getName).toList());
        assertEquals("1", result.get(1).getMetadata().get("invocationId"));
    }

    @Test
    void listDeadLetterBlobsListsOnlyTheMonthlyFoldersFromTheStartDate() {
        when(blobContainerClient.exists()).thenReturn(true);
        Map<String, List<BlobItem>> folders = Map.of(
                "2026/11/", List.of(blobItem("2026/11/30/23/a.json", FROM.plusDays(24), Map.of())),
                "2026/12/", List.of(blobItem("2026/12/01/00/b.json", FROM.plusDays(25), Map.of())));
        List<String> listedPrefixes = new ArrayList<>();
        when(blobContainerClient.listBlobs(any(ListBlobsOptions.class), isNull())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0, ListBlobsOptions.class).getPrefix();
            listedPrefixes.add(prefix);
            PagedIterable<BlobItem> page = mock(PagedIterable.class);
            when(page.stream()).thenReturn(folders.getOrDefault(prefix, List.of()).stream());
            return page;
        });

        List<DeadLetterBlob> result = sut.listDeadLetterBlobs(CONTAINER, FROM, FROM.plusDays(26), blob -> true, 10);

        assertEquals(List.of("2026/11/", "2026/12/"), listedPrefixes);
        assertEquals(List.of("2026/11/30/23/a.json", "2026/12/01/00/b.json"), result.stream().map(DeadLetterBlob::getName).toList());
    }

    @Test
    void listDeadLetterBlobsStopsListingWhenTheLimitIsReached() {
        when(blobContainerClient.exists()).thenReturn(true);
        List<String> listedPrefixes = new ArrayList<>();
        when(blobContainerClient.listBlobs(any(ListBlobsOptions.class), isNull())).thenAnswer(invocation -> {
            listedPrefixes.add(invocation.getArgument(0, ListBlobsOptions.class).getPrefix());
            PagedIterable<BlobItem> page = mock(PagedIterable.class);
            when(page.stream()).thenReturn(Stream.of(blobItem("blob", FROM, Map.of())));
            return page;
        });

        assertEquals(1, sut.listDeadLetterBlobs(CONTAINER, FROM, FROM.plusMonths(3), blob -> true, 1).size());
        assertEquals(List.of("2026/11/"), listedPrefixes);
    }

    @Test
    void monthlyPrefixesHaveOneDayOfMarginForTheTimeZone() {
        assertEquals(List.of("2026/10/", "2026/11/", "2026/12/"),
                BlobStorage.monthlyPrefixes(OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2026, 11, 30, 23, 0, 0, 0, ZoneOffset.UTC)));
        assertEquals(List.of("2026/11/"),
                BlobStorage.monthlyPrefixes(FROM, FROM.plusDays(1)));
        assertEquals(List.of("2026/12/", "2027/01/", "2027/02/"),
                BlobStorage.monthlyPrefixes(OffsetDateTime.of(2026, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2027, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)));
    }

    @Test
    void readDeadLetterBlobDoesNotLoseFields() throws IOException {
        BizEvent bizEvent = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        when(blobClient.downloadContent()).thenReturn(BinaryData.fromObject(List.of(bizEvent)));

        List<BizEvent> result = sut.readDeadLetterBlob(CONTAINER, BLOB);

        assertEquals(new Gson().toJsonTree(bizEvent), new Gson().toJsonTree(result.get(0)));
    }

    @Test
    void deleteAndUpdateDeadLetterBlob() {
        Map<String, String> metadata = Map.of("recoveryAttempts", "1");

        sut.deleteDeadLetterBlob(CONTAINER, BLOB);
        sut.updateDeadLetterBlobMetadata(CONTAINER, BLOB, metadata);

        verify(blobClient).delete();
        verify(blobClient).setMetadata(metadata);
    }

    @Test
    void uploadToDeadLetterErrorReturnsFalse() {
        when(blobServiceClient.createBlobContainerIfNotExists(anyString())).thenThrow(new IllegalStateException("error"));

        assertFalse(sut.uploadToDeadLetter("12345678-id", LocalDateTime.now(), "invocation", "type", List.of(), CONTAINER));
    }

    private BlobItem blobItem(String name, OffsetDateTime creationTime, Map<String, String> metadata) {
        return new BlobItem()
                .setName(name)
                .setMetadata(metadata)
                .setProperties(new BlobItemProperties().setCreationTime(creationTime));
    }
}
