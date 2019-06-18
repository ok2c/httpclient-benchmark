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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.VersionInfo;

public class ApacheHttpClientV4 implements HttpAgent {

    private final PoolingHttpClientConnectionManager mgr;
    private final CloseableHttpClient httpclient;

    public ApacheHttpClientV4() {
        super();
        this.mgr = new PoolingHttpClientConnectionManager();
        this.mgr.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setBufferSize(8 * 1024)
                .setFragmentSizeHint(8 * 1024)
                .build());
        this.mgr.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(60000)
                .build());
        this.httpclient = HttpClients.createMinimal(this.mgr);
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
        this.mgr.shutdown();
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        this.mgr.setMaxTotal(2000);
        this.mgr.setDefaultMaxPerRoute(config.getConcurrency());

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

            final HttpHost targetHost = new HttpHost(target.getHost(), target.getPort(), target.getScheme());
            final RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(config.getTimeout())
                    .setSocketTimeout(config.getTimeout())
                    .build();

            while (!this.stats.isComplete()) {
                final HttpUriRequest request;
                if (config.getFile() == null) {
                    request = RequestBuilder.get(target)
                            .build();
                } else {
                    request = RequestBuilder.put(target)
                            .setEntity(new FileEntity(
                                    config.getFile(),
                                    config.getContentType() != null ? ContentType.parse(config.getContentType()) : null))
                            .build();
                }
                if (!config.isKeepAlive()) {
                    request.addHeader(HttpHeaders.CONNECTION, "close");
                }

                final HttpClientContext clientContext = HttpClientContext.create();
                clientContext.setRequestConfig(requestConfig);

                long contentLen = 0;
                try (final CloseableHttpResponse response = httpclient.execute(targetHost, request, clientContext)) {
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
                    if (response.getStatusLine().getStatusCode() == 200) {
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
                "org.apache.http.client",
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpClient (ver: " + (vinfo != null ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new ApacheHttpClientV4(), args);
    }

}