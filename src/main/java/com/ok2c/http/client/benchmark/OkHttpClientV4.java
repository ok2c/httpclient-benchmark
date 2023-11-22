package com.ok2c.http.client.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpClientV4 implements HttpAgent {

    @Override
    public void init() throws Exception {
    }

    @Override
    public void shutdown() throws Exception {
    }

    private Request createRequest(BenchmarkConfig config) throws Exception {
        Request.Builder requestBuilder = new Request.Builder().url(config.getUri().toURL());
        if (config.getFile() == null) {
            requestBuilder.method("GET", null);
        } else {
            requestBuilder.method("PUT", RequestBody.create(
                    Files.readAllBytes(config.getFile().toPath()),
                    config.getContentType() != null ? MediaType.parse(config.getContentType()) : null));
            if (config.getContentType() != null) {
                requestBuilder.header("Content-Type", config.getContentType());
            }
        }
        if (!config.isKeepAlive()) {
            requestBuilder.header("Connection", "close");
        }
        return requestBuilder.build();
    }

    @Override
    public Stats execute(BenchmarkConfig config) throws Exception {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(config.getConcurrency());

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.getTimeout()))
                .readTimeout(Duration.ofMillis(config.getTimeout()))
                .build();

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final Semaphore semaphore = new Semaphore(config.getConcurrency());
        for (int i = 0; i < config.getRequests(); i++) {
            Request request = createRequest(config);

            semaphore.acquire();
            final AtomicLong contentLen = new AtomicLong(0);
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    stats.failure(contentLen.get());
                    throw new IOException("Unexpected code " + response);
                }
                contentLen.addAndGet(response.body().bytes().length);
                stats.success(contentLen.get());
            } finally {
                semaphore.release();
            }
        }
        stats.waitFor();
        return stats;
    }

    @Override
    public String getClientName() {
        return "Square’s OkHttp 4.10.0";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new OkHttpClientV4(), args);
    }
}
