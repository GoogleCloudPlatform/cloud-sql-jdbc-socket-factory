package com.google.cloud.sql.sqlserver;

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
    private static HikariDataSource dataSource;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(PORT)), 0);
        server.createContext("/", new PasswordAuthHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    private static synchronized HikariDataSource getDataSource() throws IOException {
        if (dataSource == null) {
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:sqlserver:///%s;databaseName=%s", DB_NAME, DB_NAME)); // Note: socket factory usually handles the URL structure, but mssql might be different. Actually for socket factory it's different.
            // For SQL Server Socket Factory: jdbc:sqlserver://;socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>;databaseName=<DB_NAME>

            config.setJdbcUrl(String.format(
                "jdbc:sqlserver://;" +
                "socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;" +
                "socketFactoryConstructorArg=%s;" +
                "databaseName=%s;" +
                "ipTypes=%s;" +
                "encrypt=true;" +
                "trustServerCertificate=true;", 
                INSTANCE_CONNECTION_NAME, DB_NAME, IP_TYPE
            ));

            config.setUsername(dbUser);
            config.setPassword(dbPassword);

            // The cloudSqlRefreshStrategy property allows you to configure how the Cloud SQL
            // connector refreshes its security certificates. The two supported values are:
            //
            // - "lazy": The refresh cycle is triggered when a connection is requested and the
            //   current certificate is expired. This is the default behavior.
            //
            // - "background": A background thread is started to perform the refresh cycle
            //   automatically.
            config.addDataSourceProperty("cloudSqlRefreshStrategy", "lazy");

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    static class PasswordAuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Connection conn = getDataSource().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {

                if (rs.next()) {
                    String response = "Database connection successful, result: " + rs.getInt(1);
                    sendResponse(exchange, 200, response);
                } else {
                     sendResponse(exchange, 500, "Query returned no results");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, " error: " + e.getMessage());
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
