/*
 * Copyright 2019 OK2 Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ok2c.http.client.benchmark;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class JRE11HttpClient implements HttpAgent {

    public JRE11HttpClient() {
        super();
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        System.setProperty("http.maxConnections", Integer.toString(config.getConcurrency()));

        // There appears to be no way to adjust internal buffers
        final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(config.getTimeout()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final Semaphore semaphore = new Semaphore(config.getConcurrency());
        for (int i = 0; i < config.getRequests(); i++) {
            final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            if (config.getFile() == null) {
                requestBuilder.GET();
            } else {
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofFile(config.getFile().toPath()));
                if (config.getContentType() != null) {
                    requestBuilder.header("Content-Type", config.getContentType());
                }
            }
            requestBuilder.uri(config.getUri());
            if (!config.isKeepAlive()) {
                requestBuilder.header("Connection", "close");
            }
            final HttpRequest request = requestBuilder
                    .expectContinue(false)
                    .timeout(Duration.ofMillis(config.getTimeout()))
                    .build();

            semaphore.acquire();
            final AtomicLong contentLen = new AtomicLong(0);
            final CompletableFuture<HttpResponse<Void>> future = httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.fromSubscriber(new HttpResponse.BodySubscriber<Void>() {

                        private final CompletableFuture<Void> future = new CompletableFuture<>();
                        private volatile Flow.Subscription subscription;

                        @Override
                        public void onSubscribe(final Flow.Subscription subscription) {
                            if (this.subscription != null) {
                                subscription.cancel();
                                return;
                            }
                            this.subscription = subscription;
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(final List<ByteBuffer> itemList) {
                            for (final ByteBuffer item : itemList) {
                                contentLen.addAndGet(item.remaining());
                            }
                        }

                        @Override
                        public void onError(final Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }

                        @Override
                        public void onComplete() {
                            future.complete(null);
                        }

                        @Override
                        public CompletionStage<Void> getBody() {
                            return future;
                        }

                    }));
            future.whenComplete((response, throwable) -> {
                if (response.statusCode() == 200) {
                    stats.success(contentLen.get());
                } else {
                    stats.failure(contentLen.get());
                }
                semaphore.release();
            });
        }
        stats.waitFor();
        return stats;
    }

    @Override
    public String getClientName() {
        return "JRE java.net.http " + System.getProperty("java.version");
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new JRE11HttpClient(), args);
    }

}