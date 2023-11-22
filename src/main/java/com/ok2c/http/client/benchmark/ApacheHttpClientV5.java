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
import java.io.InputStream;
import java.net.URI;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.VersionInfo;

public class ApacheHttpClientV5 implements HttpAgent {

    private final PoolingHttpClientConnectionManager mgr;
    private final CloseableHttpClient httpclient;

    public ApacheHttpClientV5() {
        super();
        this.mgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(new ManagedHttpClientConnectionFactory(
                        Http1Config.custom()
                                .setBufferSize(8 * 1024)
                                .setChunkSizeHint(8 * 1024)
                                .build(),
                        CharCodingConfig.DEFAULT,
                        DefaultHttpResponseParserFactory.INSTANCE))
                .build();
        this.httpclient = HttpClients.createMinimal(this.mgr);
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
        this.mgr.close(CloseMode.GRACEFUL);
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        this.mgr.setMaxTotal(2000);
        this.mgr.setDefaultMaxPerRoute(config.getConcurrency());
        this.mgr.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofMilliseconds(config.getTimeout()))
                .setConnectTimeout(Timeout.ofMilliseconds(config.getTimeout()))
                .build());

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final WorkerThread[] workers = new WorkerThread[config.getConcurrency()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(stats, config);
        }
        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join();
        }
        return stats;
    }

    class WorkerThread extends Thread {

        private final Stats stats;
        private final BenchmarkConfig config;

        WorkerThread(final Stats stats, final BenchmarkConfig config) {
            super();
            this.stats = stats;
            this.config = config;
        }

        @Override
        public void run() {
            final byte[] buffer = new byte[4096];

            final URI target = config.getUri();

            final HttpHost targetHost = new HttpHost(target.getScheme(), target.getHost(), target.getPort());

            while (!this.stats.isComplete()) {
                final ClassicRequestBuilder requestBuilder;
                if (config.getFile() == null) {
                    requestBuilder = ClassicRequestBuilder.get(target);
                } else {
                    requestBuilder = ClassicRequestBuilder.put(target)
                            .setEntity(new FileEntity(
                                    config.getFile(),
                                    config.getContentType() != null ? ContentType.parse(config.getContentType()) : null));
                }
                if (!config.isKeepAlive()) {
                    requestBuilder.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                }

                final ClassicHttpRequest request = requestBuilder.build();
                final HttpClientContext clientContext = HttpClientContext.create();

                long contentLen = 0;
                try (final ClassicHttpResponse response = httpclient.executeOpen(targetHost, request, clientContext)) {
                    final HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        final InputStream instream = entity.getContent();
                        contentLen = 0;
                        if (instream != null) {
                            try {
                                int l;
                                while ((l = instream.read(buffer)) != -1) {
                                    contentLen += l;
                                }
                            } finally {
                                instream.close();
                            }
                        }
                    }
                    if (response.getCode() == 200) {
                        this.stats.success(contentLen);
                    } else {
                        this.stats.failure(contentLen);
                    }
                } catch (final IOException ex) {
                    this.stats.failure(contentLen);
                }
            }
        }

    }

    @Override
    public String getClientName() {
        final VersionInfo vinfo = VersionInfo.loadVersionInfo(
                "org.apache.hc.client5",
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpClient (ver: " + (vinfo != null ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new ApacheHttpClientV5(), args);
    }

}