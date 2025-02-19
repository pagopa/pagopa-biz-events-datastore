package it.gov.pagopa.bizeventsdatastore.util;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.Getter;

@Getter
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
}
