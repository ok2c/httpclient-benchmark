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
import java.io.OutputStream;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayOutputStream2;
import org.eclipse.jetty.util.IO;

public final class BenchmarkServer {

    private final Server server;
    private final ServerConnector connector;

    public BenchmarkServer(final int port) {
        this.server = new Server();
        this.connector = new ServerConnector(this.server);
        this.connector.setPort(port);
        this.server.addConnector(this.connector);
        this.server.setHandler(new RandomDataHandler());
    }

    public void start() throws Exception {
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public int getActualPort() {
        return connector.getLocalPort();
    }

    static class RandomDataHandler extends AbstractHandler {

        public RandomDataHandler() {
            super();
        }

        @Override
        public void handle(
                final String target,
                final Request baseRequest,
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException, ServletException {
            if (target.equals("/rnd")) {
                rnd(request, response);
            } else if (target.equals("/echo")) {
                echo(request, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                final Writer writer = response.getWriter();
                writer.write("Target not found: " + target);
                writer.flush();
            }
        }

        private void rnd(
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException {
            int count;
            final String s = request.getParameter("c");
            try {
                count = Integer.parseInt(s);
            } catch (final NumberFormatException ex) {
                response.setStatus(500);
                final Writer writer = response.getWriter();
                writer.write("Invalid query format: " + request.getQueryString());
                writer.flush();
                return;
            }

            response.setStatus(200);
            response.setContentLength(count);

            final OutputStream outstream = response.getOutputStream();
            final byte[] tmp = new byte[1024];
            final int r = Math.abs(tmp.hashCode());
            int remaining = count;
            while (remaining > 0) {
                final int chunk = Math.min(tmp.length, remaining);
                for (int i = 0; i < chunk; i++) {
                    tmp[i] = (byte) ((r + i) % 96 + 32);
                }
                outstream.write(tmp, 0, chunk);
                remaining -= chunk;
            }
            outstream.flush();
        }

        private void echo(
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException {

            final ByteArrayOutputStream2 buffer = new ByteArrayOutputStream2();
            final InputStream instream = request.getInputStream();
            if (instream != null) {
                IO.copy(instream, buffer);
                buffer.flush();
            }
            final byte[] content = buffer.getBuf();
            final int len = buffer.getCount();

            response.setStatus(200);
            response.setContentLength(len);

            final OutputStream outstream = response.getOutputStream();
            outstream.write(content, 0, len);
            outstream.flush();
        }

    }

    public static void main(final String... args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        final BenchmarkServer server = new BenchmarkServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping embedded server");
            try {
                server.stop();
            } catch (Exception ignore) {
            }
        }));
        System.out.println("Embedded server is listenining on port " + server.getActualPort());
        server.join();
    }

}
