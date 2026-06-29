package com.platform.pipeline.storage.tier;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MinioColdStorageStore implements ColdStorageStore {
    private final MinioClient minioClient;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioColdStorageStore(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void write(TieredRecord record) {
        try {
            ensureBucket();
            byte[] bytes = record.payload().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName(record.key()))
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("application/json")
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("minio cold storage write failed", ex);
        }
    }

    @Override
    public List<TieredRecord> readAll() {
        try {
            ensureBucket();
            List<TieredRecord> records = new ArrayList<>();
            Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).build());
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (item.objectName().endsWith(".data")) {
                    records.add(read(item.objectName()));
                }
            }
            return records;
        } catch (Exception ex) {
            throw new IllegalStateException("minio cold storage read failed", ex);
        }
    }

    private TieredRecord read(String objectName) {
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            String key = objectName.substring(0, objectName.length() - 5);
            return new TieredRecord(key, new String(stream.readAllBytes(), StandardCharsets.UTF_8), StorageTier.COLD);
        } catch (Exception ex) {
            throw new IllegalStateException("minio cold storage read failed", ex);
        }
    }

    private synchronized void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        } catch (Exception ex) {
            throw new IllegalStateException("minio bucket init failed", ex);
        }
    }

    private String objectName(String key) {
        return key.replace('\\', '_').replace('/', '_') + ".data";
    }
}
