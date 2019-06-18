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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class HttpJRE implements HttpAgent {

    public HttpJRE() {
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

            while (!this.stats.isComplete()) {
                long contentLen = 0;
                try {
                    final URL targetUrl = target.toURL();
                    final HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                    conn.setReadTimeout(config.getTimeout());

                    final File file = config.getFile();
                    if (file != null) {
                        conn.setRequestMethod("PUT");
                        conn.setFixedLengthStreamingMode(file.length());

                        if (config.getContentType() != null) {
                            conn.addRequestProperty("Content-Type", config.getContentType());
                        }
                        if (!config.isKeepAlive()) {
                            conn.addRequestProperty("Connection", "close");
                        }
                        conn.setUseCaches (false);
                        conn.setDoInput(true);
                        conn.setDoOutput(true);
                        try (final OutputStream out = conn.getOutputStream();
                             final FileInputStream in = new FileInputStream(file)) {
                            int l;
                            while ((l = in.read(buffer)) != -1) {
                                out.write(buffer, 0, l);
                            }
                        }
                    }
                    try (final InputStream in = conn.getInputStream()) {
                        int l;
                        while ((l = in.read(buffer)) != -1) {
                            contentLen += l;
                        }
                    }
                    if (conn.getResponseCode() == 200) {
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
        return "JRE HTTP " + System.getProperty("java.version");
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new HttpJRE(), args);
    }

}