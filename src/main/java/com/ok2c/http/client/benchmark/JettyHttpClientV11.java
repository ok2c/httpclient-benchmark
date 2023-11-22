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

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.PathRequestContent;
import org.eclipse.jetty.util.Jetty;

public class JettyHttpClientV11 implements HttpAgent {

    private final HttpClient client;

    public JettyHttpClientV11() {
        super();
        this.client = new HttpClient();
        this.client.setRequestBufferSize(8 * 1024);
        this.client.setResponseBufferSize(8 * 1024);
    }

    @Override
    public void init() throws Exception {
        this.client.start();
    }

    @Override
    public void shutdown() throws Exception {
        this.client.stop();
    }

    @Override
    public Stats execute(final BenchmarkConfig config) throws Exception {
        this.client.setConnectTimeout(config.getTimeout());
        this.client.setIdleTimeout(config.getTimeout());
        this.client.setMaxConnectionsPerDestination(config.getConcurrency());

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final Semaphore semaphore = new Semaphore(config.getConcurrency());
        for (int i = 0; i < config.getRequests(); i++) {
            semaphore.acquire();
            final Request request = this.client.newRequest(config.getUri());
            if (config.getFile() != null) {
                request.method("PUT");
                request.body(new PathRequestContent(config.getContentType(), config.getFile().toPath()));
            } else {
                request.method("GET");
            }
            if (!config.isKeepAlive()) {
                request.headers(h -> h.add("Connection", "close"));
            }
            final AtomicLong contentLen = new AtomicLong(0);
            request.onResponseContentAsync(new Response.Listener.Adapter() {

                @Override
                public void onContent(final Response response, final ByteBuffer content) {
                    contentLen.addAndGet(content.remaining());
                }

            });
            request.send(result -> {
                final Throwable failure = result.getFailure();
                if (failure != null) {
                    stats.failure(contentLen.get());
                } else {
                    final Response response = result.getResponse();
                    if (response.getStatus() == 200) {
                        stats.success(contentLen.get());
                    } else {
                        stats.failure(contentLen.get());
                    }
                }
                semaphore.release();
            });
        }
        stats.waitFor();
        return stats;
    }

    @Override
    public String getClientName() {
        return "Jetty " + Jetty.VERSION;
    }

    public static void main(final String[] args) throws Exception {
        BenchmarkRunner.run(new JettyHttpClientV11(), args);
    }

}