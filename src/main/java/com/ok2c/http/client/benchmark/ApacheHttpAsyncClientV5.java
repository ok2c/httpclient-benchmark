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

import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.FileEntityProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.VersionInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

public class ApacheHttpAsyncClientV5 implements HttpAgent {

    private final PoolingAsyncClientConnectionManager mgr;
    private final CloseableHttpAsyncClient httpclient;

    public ApacheHttpAsyncClientV5() throws Exception {
        super();
        this.mgr = PoolingAsyncClientConnectionManagerBuilder.create()
                .build();
        this.httpclient = HttpAsyncClients.createMinimal(
                HttpVersionPolicy.FORCE_HTTP_1,
                H2Config.DEFAULT,
                Http1Config.custom()
                        .setBufferSize(8 * 1024)
                        .setChunkSizeHint(8 * 1024)
                        .build(),
                IOReactorConfig.custom()
                        .setRcvBufSize(8 * 1024)
                        .setSndBufSize(8 * 1024)
                        .setSoTimeout(Timeout.ofMinutes(1))
                        .build(),
                this.mgr);
    }

    @Override
    public void init() {
        this.httpclient.start();
    }

    @Override
    public void shutdown() throws IOException {
        this.httpclient.close();
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        this.mgr.setDefaultMaxPerRoute(config.getConcurrency());
        this.mgr.setMaxTotal(2000);
        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());

        final URI target = config.getUri();
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(config.getTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(config.getTimeout()))
                .build();

        final Semaphore semaphore = new Semaphore(config.getConcurrency());
        for (int i = 0; i < config.getRequests(); i++) {
            final AsyncRequestBuilder requestBuilder;
            if (config.getFile() == null) {
                requestBuilder = AsyncRequestBuilder.get(target);
            } else {
                requestBuilder = AsyncRequestBuilder.put(target)
                        .setEntity(new FileEntityProducer(
                                config.getFile(),
                                config.getContentType() != null ? ContentType.parse(config.getContentType()) : null));
            }
            if (!config.isKeepAlive()) {
                requestBuilder.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            }
            final AsyncRequestProducer request = requestBuilder.build();
            final HttpClientContext clientContext = HttpClientContext.create();
            clientContext.setRequestConfig(requestConfig);
            semaphore.acquire();
            this.httpclient.execute(
                    request,
                    new BenchmarkResponseConsumer(stats),
                    clientContext,
                    new FutureCallback<Void>() {

                        @Override
                        public void completed(final Void result) {
                            semaphore.release();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            semaphore.release();
                        }

                        @Override
                        public void cancelled() {
                            semaphore.release();
                        }

                    });
        }

        stats.waitFor();
        return stats;
    }

    static class BenchmarkResponseConsumer extends AbstractBinResponseConsumer<Void> {

        private final Stats stats;

        private int status;
        private long contentLen = 0;

        BenchmarkResponseConsumer(final Stats stats) {
            super();
            this.stats = stats;
        }

        @Override
        protected void start(final HttpResponse response, final ContentType contentType) throws HttpException, IOException {
            status = response.getCode();
        }

        @Override
        protected int capacityIncrement() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
            contentLen += src.remaining();
        }

        @Override
        public void failed(final Exception ex) {
            stats.failure(contentLen);
        }

        @Override
        protected Void buildResult() {
            if (this.status == 200) {
                stats.success(contentLen);
            } else {
                stats.failure(contentLen);
            }
            return null;
        }

        @Override
        public void releaseResources() {
        }

    }

    @Override
    public String getClientName() {
        final VersionInfo vinfo = VersionInfo.loadVersionInfo(
                "org.apache.hc.client5",
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpAsyncClient (ver: " + (vinfo != null ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new ApacheHttpAsyncClientV5(), args);
    }

}