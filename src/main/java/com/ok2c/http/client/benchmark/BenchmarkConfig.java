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
import java.net.URI;

public final class BenchmarkConfig {

    private final URI uri;
    private final int requests;
    private final int concurrency;
    private final boolean keepAlive;
    private final File file;
    private final String contentType;
    private final int timeout;

    private BenchmarkConfig(
            final URI uri,
            final int requests,
            final int concurrency,
            final boolean keepAlive,
            final File file,
            final String contentType,
            final int timeout) {
        this.uri = uri;
        this.requests = requests;
        this.concurrency = concurrency;
        this.keepAlive = keepAlive;
        this.file = file;
        this.contentType = contentType;
        this.timeout = timeout;
    }

    public static Builder create() {
        return new Builder();
    }

    public static Builder copy(final BenchmarkConfig config) {
        return new Builder()
                .setUri(config.getUri())
                .setRequests(config.getRequests())
                .setConcurrency(config.getConcurrency())
                .setKeepAlive(config.isKeepAlive())
                .setFile(config.getFile())
                .setContentType(config.getContentType())
                .setTimeout(config.getTimeout());
    }

    public URI getUri() {
        return uri;
    }

    public int getRequests() {
        return requests;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public File getFile() {
        return file;
    }

    public String getContentType() {
        return contentType;
    }

    public int getTimeout() {
        return timeout;
    }

    public final static class Builder {

        private URI uri;
        private int requests;
        private int concurrency;
        private boolean keepAlive;
        private File file;
        private String contentType;
        private int timeout;

        private Builder() {
            super();
            this.requests = 1;
            this.concurrency = 1;
            this.keepAlive = false;
            this.timeout = 60000;
        }

        public URI getUri() {
            return uri;
        }

        public Builder setUri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public int getRequests() {
            return requests;
        }

        public Builder setRequests(final int requests) {
            this.requests = requests;
            return this;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public Builder setConcurrency(final int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public Builder setKeepAlive(final boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public File getFile() {
            return file;
        }

        public Builder setFile(final File file) {
            this.file = file;
            return this;
        }

        public String getContentType() {
            return contentType;
        }

        public Builder setContentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public int getTimeout() {
            return timeout;
        }

        public Builder setTimeout(final int timeout) {
            this.timeout = timeout;
            return this;
        }

        public BenchmarkConfig build() {
            return new BenchmarkConfig(uri, requests, concurrency, keepAlive, file, contentType, timeout);
        }

    }

    @Override
    public String toString() {
        return "BenchmarkConfig{" +
                "uri=" + uri +
                ", requests=" + requests +
                ", concurrency=" + concurrency +
                ", keepAlive=" + keepAlive +
                ", file=" + file +
                ", contentType='" + contentType + '\'' +
                ", timeout=" + timeout +
                '}';
    }

}
