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
import java.net.URISyntaxException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class BenchmarkRunner {

    public static void run(final HttpAgent agent, final String... args) throws Exception {
        final Options options = BenchmarkRunner.getOptions();
        if (args.length == 0) {
            printUsage(options);
            return;
        }
        try {
            final BenchmarkConfig config = BenchmarkRunner.parseConfig(options, args);
            final File file = config.getFile();
            if (file != null) {
                if (!file.exists()) {
                    System.out.println("File '" + file + "' does not exist");
                    return;
                }
                if (!file.canRead()) {
                    System.out.println("File '" + file + "' cannot be read");
                    return;
                }
            }
            BenchmarkRunner.execute(agent, config);
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            System.out.println();
            printUsage(options);
        }
    }

    static Options getOptions() {
        final Option copt = new Option("c", true, "Concurrency while performing the " +
                "benchmarking session. The default is to just use a single thread/client");
        copt.setRequired(false);
        copt.setArgName("concurrency");

        final Option nopt = new Option("n", true, "Number of requests to perform for the " +
                "benchmarking session. The default is to just perform a single " +
                "request which usually leads to non-representative benchmarking " +
                "results");
        nopt.setRequired(false);
        nopt.setArgName("requests");

        final Option kopt = new Option("k", false, "Enable the HTTP KeepAlive feature, " +
                "i.e., perform multiple requests within one HTTP session. " +
                "Default is no KeepAlive");
        kopt.setRequired(false);

        final Option popt = new Option("p", true, "Execute PUT request with the file content");
        popt.setRequired(false);
        nopt.setArgName("file path");

        final Option topt = new Option("t", true, "Content type of PUT request");
        popt.setRequired(false);
        nopt.setArgName("content type");

        final Options options = new Options();
        options.addOption(nopt);
        options.addOption(copt);
        options.addOption(kopt);
        options.addOption(popt);
        options.addOption(topt);

        return options;
    }

    static void printUsage(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("benchmark [options] <target-URI>]", options);
    }

    static BenchmarkConfig parseConfig(final Options options, final String... args) throws ParseException {
        final BenchmarkConfig.Builder builder = BenchmarkConfig.create();
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption('c')) {
            final String s = cmd.getOptionValue('c');
            try {
                builder.setConcurrency(Integer.parseInt(s));
            } catch (final NumberFormatException ex) {
                throw new ParseException("Invalid number for concurrency: " + s);
            }
        }
        if (cmd.hasOption('n')) {
            final String s = cmd.getOptionValue('n');
            try {
                builder.setRequests(Integer.parseInt(s));
            } catch (final NumberFormatException ex) {
                throw new ParseException("Invalid number of requests: " + s);
            }
        }
        if (cmd.hasOption('k')) {
            builder.setKeepAlive(true);
        }
        if (cmd.hasOption('p')) {
            builder.setFile(new File(cmd.getOptionValue('p')));
            if (cmd.hasOption('t')) {
                builder.setContentType(cmd.getOptionValue('t'));
            }
        }
        final String[] cmdargs = cmd.getArgs();
        if (cmdargs.length > 0) {
            try {
                builder.setUri(new URI(cmdargs[0]));
            } catch (final URISyntaxException ex) {
                throw new ParseException("Invalid target-URI: " + cmdargs[0]);
            }
        } else {
            throw new ParseException("Target-URI not specified");
        }
        builder.setTimeout(15000);
        return builder.build();
    }

    static void execute(final HttpAgent agent, final BenchmarkConfig config) throws Exception {
        agent.init();
        try {
            System.out.println("=================================");
            System.out.println("HTTP agent: " + agent.getClientName());
            System.out.println("=================================");
            System.out.println("warming up...");

            int warmup = config.getRequests() / 100;
            if (warmup > 100) {
                warmup = 100;
            }

            agent.execute(BenchmarkConfig.copy(config)
                    .setRequests(warmup)
                    .setConcurrency(2)
                    .build());
            // Sleep a little
            Thread.sleep(5000);

            System.out.println("---------------------------------");

            if (config.getFile() != null) {
                System.out.println(config.getRequests() + " PUT requests");
            } else {
                System.out.println(config.getRequests() + " GET requests");
            }
            System.out.println("---------------------------------");

            final long startTime = System.currentTimeMillis();
            final Stats stats = agent.execute(config);
            final long finishTime = System.currentTimeMillis();

            Stats.printStats(config.getUri(), startTime, finishTime, stats);
        } finally {
            agent.shutdown();
        }
    }

}
