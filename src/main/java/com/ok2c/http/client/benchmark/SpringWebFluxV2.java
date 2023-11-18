package com.ok2c.http.client.benchmark;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class SpringWebFluxV2 implements HttpAgent {

    @Override
    public void init() throws Exception {
    }

    @Override
    public void shutdown() throws Exception {
    }

    @Override
    public Stats execute(BenchmarkConfig config) throws Exception {
        Scheduler scheduler = Schedulers.newParallel("WorkerThread", config.getConcurrency());
        WebClient webClient = WebClient.create();

        final Stats stats = new Stats(config.getRequests(), config.getConcurrency());
        final Semaphore semaphore = new Semaphore(config.getConcurrency());

        for (int i = 0; i < config.getRequests(); i++) {
            semaphore.acquire();
            final AtomicLong contentLen = new AtomicLong(0);
            webClient.get().uri(config.getUri())
                    .headers(httpHeaders -> {
                        if (config.getFile() != null) {
                            httpHeaders.set("Content-Type", config.getContentType());
                        }
                        if (!config.isKeepAlive()) {
                            httpHeaders.set("Connection", "close");
                        }
                    })
                    .retrieve()
                    .bodyToMono(ByteArrayResource.class)
                    .doOnSuccess(response -> {
                        contentLen.addAndGet(response.contentLength());
                        stats.success(contentLen.get());
                    })
                    .doOnError(response -> {
                        stats.failure(contentLen.get());
                    }).doFinally(signal -> {
                        semaphore.release();
                    }).publishOn(scheduler).block();
        }
        stats.waitFor();
        return stats;
    }

    @Override
    public String getClientName() {
        return "Spring WebFlux WebClient 2.7.6";
    }

    public static void main(final String... args) throws Exception {
        BenchmarkRunner.run(new SpringWebFluxV2(), args);
    }
}
