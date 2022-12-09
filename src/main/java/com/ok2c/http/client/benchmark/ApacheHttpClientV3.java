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

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ApacheHttpClientV3 implements HttpAgent {

    private final MultiThreadedHttpConnectionManager mgr;
    private final HttpClient httpclient;

    public ApacheHttpClientV3() {
        super();
        this.mgr = new MultiThreadedHttpConnectionManager();
        this.httpclient = new HttpClient(this.mgr);
        this.httpclient.getParams().setVersion(HttpVersion.HTTP_1_1);
        this.httpclient.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, false);
        this.httpclient.getHttpConnectionManager().getParams().setStaleCheckingEnabled(false);
        final HttpMethodRetryHandler retryhandler = (httpmethod, ex, count) -> false;
        this.httpclient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, retryhandler);
    }

    public void init() {
    }

    public void shutdown() {
        this.mgr.shutdown();
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        this.mgr.getParams().setMaxTotalConnections(2000);
        this.mgr.getParams().setDefaultMaxConnectionsPerHost(config.getConcurrency());
        this.mgr.getParams().setConnectionTimeout(config.getTimeout());
        this.mgr.getParams().setSoTimeout(config.getTimeout());

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final WorkerThread[] workers = new WorkerThread[config.getConcurrency()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(stats, config);
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
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

            while (!this.stats.isComplete()) {
                final HttpMethod httpmethod;
                if (this.config.getFile() == null) {
                    httpmethod = new GetMethod(target.toASCIIString());
                } else {
                    final PutMethod httppost = new PutMethod(target.toASCIIString());
                    httppost.setRequestEntity(new FileRequestEntity(this.config.getFile(), this.config.getContentType()));
                    httpmethod = httppost;
                }
                if (!config.isKeepAlive()) {
                    httpmethod.addRequestHeader("Connection", "close");
                }
                long contentLen = 0;
                try {
                    httpclient.executeMethod(httpmethod);
                    final InputStream instream = httpmethod.getResponseBodyAsStream();
                    if (instream != null) {
                        int l;
                        while ((l = instream.read(buffer)) != -1) {
                            contentLen += l;
                        }
                    }
                    if (httpmethod.getStatusCode() == 200) {
                        this.stats.success(contentLen);
                    } else {
                        this.stats.failure(contentLen);
                    }
                } catch (IOException ex) {
                    this.stats.failure(contentLen);
                } finally {
                    httpmethod.releaseConnection();
                }
            }
        }

    }

    public String getClientName() {
        return "Apache HttpClient 3.1";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new ApacheHttpClientV3(), args);
    }

}
