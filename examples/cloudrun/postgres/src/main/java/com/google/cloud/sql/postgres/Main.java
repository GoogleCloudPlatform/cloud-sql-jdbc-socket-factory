package com.google.cloud.sql.postgres;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class Main {

    private static final String INSTANCE_CONNECTION_NAME = System.getenv("INSTANCE_CONNECTION_NAME");
    private static final String DB_NAME = System.getenv("DB_NAME");
    private static final String IP_TYPE = System.getenv().getOrDefault("IP_TYPE", "PUBLIC");
    private static final String PORT = System.getenv().getOrDefault("PORT", "8080");

    // HikariDataSources are thread-safe and should be used as a global object.
    //
    // Lazy instantiation (initializing the Connector and Engine only when needed)
    // allows the Cloud Run service to start up faster, as it avoids performing
    // initialization tasks (like fetching secrets or metadata) during startup.
    private static HikariDataSource passwordDataSource;
    private static HikariDataSource iamDataSource;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(PORT)), 0);
        server.createContext("/", new PasswordAuthHandler());
        server.createContext("/iam", new IamAuthHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    private static synchronized HikariDataSource getPasswordDataSource() throws IOException {
        if (passwordDataSource == null) {
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql:///%s", DB_NAME));
            config.setUsername(dbUser);
            config.setPassword(dbPassword);
            config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
            config.addDataSourceProperty("cloudSqlInstance", INSTANCE_CONNECTION_NAME);
            config.addDataSourceProperty("ipTypes", IP_TYPE);
            // The cloudSqlRefreshStrategy property allows you to configure how the Cloud SQL
            // connector refreshes its security certificates. The two supported values are:
            //
            // - "lazy": The refresh cycle is triggered when a connection is requested and the
            //   current certificate is expired. This is the default behavior.
            //
            // - "background": A background thread is started to perform the refresh cycle
            //   automatically.
            config.addDataSourceProperty("cloudSqlRefreshStrategy", "lazy");

            passwordDataSource = new HikariDataSource(config);
        }
        return passwordDataSource;
    }

    private static synchronized HikariDataSource getIamDataSource() {
        if (iamDataSource == null) {
            String dbUser = System.getenv("DB_IAM_USER");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql:///%s", DB_NAME));
            config.setUsername(dbUser);
            config.setPassword("password"); // Postgres requires a password even if ignored
            config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
            config.addDataSourceProperty("cloudSqlInstance", INSTANCE_CONNECTION_NAME);
            config.addDataSourceProperty("ipTypes", IP_TYPE);
            config.addDataSourceProperty("enableIamAuth", "true");
            config.addDataSourceProperty("sslmode", "disable");
            // The cloudSqlRefreshStrategy property allows you to configure how the Cloud SQL
            // connector refreshes its security certificates. The two supported values are:
            //
            // - "lazy": The refresh cycle is triggered when a connection is requested and the
            //   current certificate is expired. This is the default behavior.
            //
            // - "background": A background thread is started to perform the refresh cycle
            //   automatically.
            config.addDataSourceProperty("cloudSqlRefreshStrategy", "lazy");

            iamDataSource = new HikariDataSource(config);
        }
        return iamDataSource;
    }

    private static String getSecret(String secretName) throws IOException {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            AccessSecretVersionResponse response = client.accessSecretVersion(secretName);
            return response.getPayload().getData().toStringUtf8();
        }
    }

    static class PasswordAuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Connection conn = getPasswordDataSource().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {

                if (rs.next()) {
                    String response = "Database connection successful (password authentication), result: " + rs.getInt(1);
                    sendResponse(exchange, 200, response);
                } else {
                     sendResponse(exchange, 500, "Query returned no results");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    static class IamAuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
             try (Connection conn = getIamDataSource().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {

                if (rs.next()) {
                    String response = "Database connection successful (IAM authentication), result: " + rs.getInt(1);
                    sendResponse(exchange, 200, response);
                } else {
                     sendResponse(exchange, 500, "Query returned no results");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
