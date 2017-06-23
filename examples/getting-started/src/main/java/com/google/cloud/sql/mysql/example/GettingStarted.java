package com.google.cloud.sql.mysql.example;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.InstancesListResponse;
import com.google.common.collect.ImmutableSet;
import java.io.Console;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;

public class GettingStarted {
  private static final ImmutableSet<String> SYSTEM_DATABASES =
      ImmutableSet.of(
          // MySQL.
          "mysql", "information_schema", "performance_schema",
          // Postgres.
          "cloudsqladmin", "postgres");

  @Parameter(names = "-v", description = "Verbose logging.")
  private boolean verbose = false;

  private void run() throws IOException, SQLException {
    System.out.println("Checking API credentials.");
    GoogleCredential apiCredentials;
    try {
      apiCredentials = GoogleCredential.getApplicationDefault();
    } catch (IOException e) {
      System.err.println(
          "Unable to find API credentials. \nPlease run "
              + "'gcloud auth application-default login' to make credentials available to "
              + "this application.");
      if (verbose) {
        e.printStackTrace();
      }
      System.exit(-1);
      return;
    }

    SQLAdmin adminApiClient = createAdminApiClient(apiCredentials);
    Optional<List<DatabaseInstance>> instances = askForProject(adminApiClient);
    if (!instances.isPresent()) {
      return;
    }

    if (instances.get().isEmpty()) {
      System.out.println(
          "This project does not contain any Cloud SQL instances. "
              + "Please create one using the Cloud Console.");
      return;
    }

    Optional<DatabaseInstance> optionalInstance = askForInstance(instances.get());
    if (!optionalInstance.isPresent()) {
      return;
    }

    String instanceConnectionName = optionalInstance.get().getConnectionName();
    Optional<DatabaseCredentials> optionalDatabaseCredentials =
        askForDatabaseCredentials(optionalInstance.get());
    if (!optionalDatabaseCredentials.isPresent()) {
      return;
    }

    Connection connection = optionalDatabaseCredentials.get().getConnection();
    List<String> databases = listDatabases(optionalInstance.get(), connection);
    connection.close();
    if (databases.isEmpty()) {
      printConnectionDetails(
          optionalInstance.get(), Optional.empty(), optionalDatabaseCredentials.get());
      return;
    }

    Optional<String> database = askForDatabase(databases);
    if (!database.isPresent()) {
      return;
    }

    printConnectionDetails(optionalInstance.get(), database, optionalDatabaseCredentials.get());
  }

  private List<String> listDatabases(
      DatabaseInstance databaseInstance, Connection connection) throws SQLException {
    String listDatabasesQuery;
    switch (getDatabaseType(databaseInstance)) {
      case MYSQL:
        listDatabasesQuery = "SHOW DATABASES";
        break;
      case POSTGRES:
        listDatabasesQuery =
            "SELECT datname AS database FROM pg_database WHERE datistemplate = false";
        break;
      default:
        throw new IllegalStateException();
    }

    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery(listDatabasesQuery);
    List<String> databases = new ArrayList<>();
    while (resultSet.next()) {
      String database = resultSet.getString("database");
      if (SYSTEM_DATABASES.contains(database)) {
        continue;
      }
      databases.add(database);
    }
    statement.close();
    databases.sort(String::compareTo);
    return databases;
  }

  private Optional<DatabaseCredentials> askForDatabaseCredentials(DatabaseInstance databaseInstance)
      throws SQLException {

    String defaultUser;
    String displayDatabaseType;
    String defaultDatabase;
    switch (getDatabaseType(databaseInstance)) {
      case MYSQL:
        defaultUser = "root";
        displayDatabaseType = "MySQL";
        defaultDatabase = "mysql";
        break;
      case POSTGRES:
        defaultUser = "postgres";
        displayDatabaseType = "Postgres";
        defaultDatabase = "postgres";
        break;
      default:
        return Optional.empty();
    }

    Console console = System.console();
    String user;
    String lastUser = defaultUser;
    for (; ; ) {
      char[] password;
      System.out.printf("Please enter %s username [%s]: ", displayDatabaseType, lastUser);
      user = console.readLine();
      if (user == null) {
        return Optional.empty();
      }

      if (user.trim().isEmpty()) {
        user = lastUser;
      } else {
        lastUser = user;
      }

      System.out.printf("Please enter %s password: ", displayDatabaseType);
      password = console.readPassword();
      if (password == null) {
        return Optional.empty();
      }

      try {
        return Optional.of(
            new DatabaseCredentials(
                user,
                password,
                DriverManager.getConnection(
                    constructJdbcUrl(databaseInstance, defaultDatabase),
                    user,
                    new String(password))));
      } catch (SQLException e) {
        if (e.getErrorCode() == 1045) {
          System.out.println("Invalid username/password. Please try again.");
          continue;
        }
        // Too bad Postgres doesn't set the error code...
        if (e instanceof PSQLException
            && e.getMessage().contains("password authentication failed")) {
          System.out.println("Invalid username/password. Please try again.");
          continue;
        }
        throw e;
      }
    }
  }

  private static String constructJdbcUrl(DatabaseInstance databaseInstance, String database) {
    switch (getDatabaseType(databaseInstance)) {
      case MYSQL:
        return String.format(
            "jdbc:mysql://google/%s?socketFactory=com.google.cloud.sql.mysql.SocketFactory" +
                "&cloudSqlInstance=%s",
            database,
            databaseInstance.getConnectionName());
      case POSTGRES:
        return String.format(
            "jdbc:postgresql://google/%s?socketFactory=com.google.cloud.sql.postgres.SocketFactory" +
                "&socketFactoryArg=%s",
            database,
            databaseInstance.getConnectionName());
      default:
        throw new IllegalStateException();
    }
  }

  private Optional<DatabaseInstance> askForInstance(List<DatabaseInstance> instances) {
    Optional<Integer> instanceChoice =
        chooseFromList(
            "Please enter the number of the instance you want to use [1]: ",
            instances.stream()
                .map(
                    inst ->
                        String.format(
                            "%s [%s] (%s)",
                            inst.getName(),
                            inst.getDatabaseVersion(),
                            inst.getConnectionName()))
                .collect(Collectors.toList()));
    if (!instanceChoice.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(instances.get(instanceChoice.get()));
  }

  private Optional<String> askForDatabase(List<String> databases) {
    Optional<Integer> databaseIndex =
        chooseFromList("Please enter the number of the database you want to use [1]: ", databases);
    if (!databaseIndex.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(databases.get(databaseIndex.get()));
  }

  private Optional<Integer> chooseFromList(String prompt, List<String> options) {
    Console console = System.console();

    for (int i = 0; i < options.size(); i++) {
      System.out.println(String.format("%d: %s", i + 1, options.get(i)));
    }

    int choice;
    for (;;) {
      System.out.print(prompt);
      String line = console.readLine();
      if (line == null) {
        return Optional.empty();
      }

      if (line.trim().isEmpty()) {
        return Optional.of(0);
      } else {
        try {
          choice = Integer.parseInt(line);
        } catch (NumberFormatException e) {
          System.out.println("Invalid choice.");
          continue;
        }

        if (choice < 1 || choice > options.size()) {
          System.out.println("Invalid choice.");
          continue;
        }

        return Optional.of(choice - 1);
      }
    }
  }

  private Optional<List<DatabaseInstance>> askForProject(SQLAdmin adminApiClient)
      throws IOException {
    Console console = System.console();

    InstancesListResponse instancesListResponse = null;
    while (instancesListResponse == null) {
      String project = "";
      while (project.isEmpty()) {
        System.out.print("Enter the name of your Cloud project: ");
        project = console.readLine();
        if (project == null) {
          return Optional.empty();
        }
        project = project.trim();
      }

      System.out.println("Listing Cloud SQL instances.");

      try {
        instancesListResponse = adminApiClient.instances().list(project).execute();
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
          System.out.println("That doesn't appear to be a valid project, try again.");
          continue;
        }
        throw e;
      }
    }

    ArrayList<DatabaseInstance> instances = new ArrayList<>(instancesListResponse.getItems());
    instances.sort(Comparator.comparing(DatabaseInstance::getName));
    return Optional.of(instances);
  }

  private void printConnectionDetails(
      DatabaseInstance databaseInstance,
      Optional<String> database,
      DatabaseCredentials databaseCredentials) {
    String databaseName = database.orElse("<database_name>");

    System.out.println("\n\n");
    System.out.printf(
        "Use the following JDBC URL%s:\n\n    %s\n",
        !database.isPresent() ? " after creating a database" : "",
        constructJdbcUrl(databaseInstance, databaseName));
    System.out.println();
    System.out.println("    Username: " + databaseCredentials.getUsername());
    System.out.println(
        "    Password: " + (databaseCredentials.getPassword().length > 0 ? "<yes>" : "<empty>"));
    System.out.println("\n\n");
  }

  private static SQLAdmin createAdminApiClient(Credential credential) {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Unable to initialize HTTP transport", e);
    }

    return new SQLAdmin.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential)
        .setApplicationName("Cloud SQL Example")
        .build();
  }

  private static final class DatabaseCredentials {
    private final String username;
    private final char[] password;
    private final Connection connection;

    public DatabaseCredentials(String username, char[] password, Connection connection) {
      this.username = username;
      this.password = password;
      this.connection = connection;
    }

    public String getUsername() {
      return username;
    }

    public char[] getPassword() {
      return password;
    }

    public Connection getConnection() {
      return connection;
    }
  }

  private static DatabaseType getDatabaseType(DatabaseInstance databaseInstance) {
    if (databaseInstance.getDatabaseVersion().startsWith("MYSQL_")) {
      return DatabaseType.MYSQL;
    } else if (databaseInstance.getDatabaseVersion().startsWith("POSTGRES_")) {
      return DatabaseType.POSTGRES;
    } else {
      System.err.println("Unsupported database type: " + databaseInstance.getDatabaseVersion());
      System.exit(-1);
      return null;
    }
  }

  private enum DatabaseType {
    MYSQL,
    POSTGRES
  }

  public static void main(String[] args) throws IOException, SQLException {
    GettingStarted gettingStarted = new GettingStarted();
    new JCommander(gettingStarted, args);
    gettingStarted.run();
  }
}
