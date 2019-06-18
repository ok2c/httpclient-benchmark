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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.VersionInfo;

public class ApacheHttpAsyncClientV4 implements HttpAgent {

    private final ConnectingIOReactor ioreactor;
    private final PoolingNHttpClientConnectionManager mgr;
    private final CloseableHttpAsyncClient httpclient;

    public ApacheHttpAsyncClientV4() throws Exception {
        super();
        this.ioreactor = new DefaultConnectingIOReactor(IOReactorConfig.custom()
                .setConnectTimeout(60000)
                .setSoTimeout(60000)
                .build());
        this.mgr = new PoolingNHttpClientConnectionManager(this.ioreactor);
        this.mgr.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setBufferSize(8 * 1024)
                .setFragmentSizeHint(8 * 1024)
                .build());
        this.httpclient = HttpAsyncClients.createMinimal(this.mgr);
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
                .setConnectTimeout(config.getTimeout())
                .setSocketTimeout(config.getTimeout())
                .build();

        final Semaphore semaphore = new Semaphore(config.getConcurrency());
        for (int i = 0; i < config.getRequests(); i++) {
            final HttpRequest request;
            if (config.getFile() == null) {
                request = RequestBuilder.get(target)
                        .build();
            } else {
                request = RequestBuilder.put(target)
                        .setEntity(new NFileEntity(
                                config.getFile(),
                                config.getContentType() != null ? ContentType.parse(config.getContentType()) : null))
                        .build();
            }
            if (!config.isKeepAlive()) {
                request.addHeader(HttpHeaders.CONNECTION, "close");
            }
            final HttpHost targetHost = new HttpHost(target.getHost(), target.getPort(), target.getScheme());
            final HttpClientContext clientContext = HttpClientContext.create();
            clientContext.setRequestConfig(requestConfig);
            semaphore.acquire();
            this.httpclient.execute(
                    new BasicAsyncRequestProducer(targetHost, request),
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

    static class BenchmarkResponseConsumer implements HttpAsyncResponseConsumer<Void> {

        private final Stats stats;

        private ByteBuffer bbuf;
        private int status;
        private long contentLen = 0;
        private Exception ex;
        private boolean done = false;

        BenchmarkResponseConsumer(final Stats stats) {
            super();
            this.stats = stats;
        }

        @Override
        public void close() throws IOException {
            if (!this.done) {
                this.done = true;
                this.stats.failure(contentLen);
            }
            bbuf = null;
        }

        @Override
        public boolean cancel() {
            bbuf = null;
            return false;
        }

        @Override
        public void responseReceived(final HttpResponse response) throws IOException, HttpException {
            this.status = response.getStatusLine().getStatusCode();
        }

        @Override
        public void consumeContent(final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            if (this.bbuf == null) {
                this.bbuf = ByteBuffer.allocate(4096);
            }
            for (;;) {
                final int bytesRead = decoder.read(this.bbuf);
                if (bytesRead <= 0) {
                    break;
                }
                this.contentLen += bytesRead;
                this.bbuf.clear();
            }
        }

        @Override
        public void responseCompleted(final HttpContext context) {
        }

        @Override
        public void failed(final Exception ex) {
            this.ex = ex;
        }

        @Override
        public Exception getException() {
            return this.ex;
        }

        @Override
        public Void getResult() {
            if (this.status == 200 && this.ex == null) {
                stats.success(contentLen);
            } else {
                stats.failure(contentLen);
            }
            this.done = true;
            return null;
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

    };

    @Override
    public String getClientName() {
        final VersionInfo vinfo = VersionInfo.loadVersionInfo(
                "org.apache.http.nio.client",
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpAsyncClient (ver: " + (vinfo != null ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new ApacheHttpAsyncClientV4(), args);
    }

}