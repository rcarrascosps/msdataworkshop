package io.helidon.data.examples;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.LogManager;

import io.helidon.data.examples.service.OrderStreamingService;

import io.helidon.config.Config;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * Main class.
 * Picked up from java -jar and defined in project's pom.
 *
 */
public final class Main {

  public static void main(final String[] args)
      throws IOException, SQLException {
    LogManager
        .getLogManager()
        .readConfiguration(
            Main.class.getResourceAsStream("/logging.properties"));
    Config config = Config.create();
    ServerConfiguration serverConfig = ServerConfiguration
        .create(config.get("server"));
    WebServer server = WebServer.create(serverConfig, createRouting(config));
    server.start().thenAccept(ws -> {
        System.out
          .printf("WEB server is up! http://localhost:%s/orderstreaming%n", ws.port());
          ws.whenShutdown()
          .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
      }).exceptionally(t -> {
          System.err.printf("Startup failed: %s%n", t.getMessage());
          t.printStackTrace(System.err);
      return null;
      }
    );
  }

  private static Routing createRouting(Config config) throws SQLException {

    // Creates a Helidon's Service implementation.
    // Use database configuration from application.yaml that
    // can be over-ridden by System.properties
    OrderStreamingService rsiService = new OrderStreamingService(config.get("database"));

    // Create routing and register
    return Routing
        .builder()
        .register(JsonSupport.create())
        .register("/orderstreaming", rsiService)
        .build();
  }
}