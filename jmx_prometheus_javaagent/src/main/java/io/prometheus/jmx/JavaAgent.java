package io.prometheus.jmx;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import org.yaml.snakeyaml.Yaml;

public class JavaAgent {

    static HTTPServer server;

    public static void agentmain(String agentArgument, Instrumentation instrumentation) throws Exception {
        premain(agentArgument, instrumentation);
    }

    public static void premain(String agentArgument, Instrumentation instrumentation) throws Exception {
        // Bind to all interfaces by default (this includes IPv6).
        String host = "0.0.0.0";

        try {
            Config config = parseConfig(agentArgument, host);

            new BuildInfoCollector().register();
            new JmxCollector(new File(config.file)).register();
            DefaultExports.initialize();
            server = new HTTPServer(config.socket, CollectorRegistry.defaultRegistry, true);
        }
        catch (IllegalArgumentException e) {
            System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration file> " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parse the Java Agent configuration. The arguments are typically specified to the JVM as a javaagent as
     * {@code -javaagent:/path/to/agent.jar=<CONFIG>}. This method parses the {@code <CONFIG>} portion.
     * @param args provided agent args
     * @param ifc default bind interface
     * @return configuration to use for our application
     */
    public static Config parseConfig(String args, String ifc) throws IOException {
        Pattern pattern = Pattern.compile(
                "^(.+)");                     // config file

        Matcher matcher = pattern.matcher(args);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed arguments - " + args);
        }

        String givenConfigFile = matcher.group(1);

        File in = new File(givenConfigFile);
        Map<String, Object> configs= (Map<String, Object>)new Yaml().load(new FileReader(in));
        String givenHost = (String) configs.get("httpHost");
        Integer port = (Integer) configs.get("httpPort");

        InetSocketAddress socket;
        if (givenHost != null && !givenHost.isEmpty()) {
            socket = new InetSocketAddress(givenHost, port);
        }
        else {
            socket = new InetSocketAddress(ifc, port);
            givenHost = ifc;
        }

        return new Config(givenHost, port, givenConfigFile, socket);
    }

    static class Config {
        String host;
        int port;
        String file;
        InetSocketAddress socket;

        Config(String host, int port, String file, InetSocketAddress socket) {
            this.host = host;
            this.port = port;
            this.file = file;
            this.socket = socket;
        }
    }
}
