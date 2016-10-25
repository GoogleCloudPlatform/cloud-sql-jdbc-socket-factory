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

public class GettingStarted {
  private static final ImmutableSet<String> SYSTEM_DATABASES =
      ImmutableSet.of("mysql", "information_schema", "performance_schema");

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

    Optional<String> optionalInstanceConnectionName = askForInstance(instances.get());
    if (!optionalInstanceConnectionName.isPresent()) {
      return;
    }
    String instanceConnectionName = optionalInstanceConnectionName.get();
    Optional<MysqlCredentials> optionalMysqlCredentials =
        askForMysqlCredentials(instanceConnectionName);
    if (!optionalMysqlCredentials.isPresent()) {
      return;
    }

    Connection connection = optionalMysqlCredentials.get().getConnection();
    List<String> databases = listDatabases(connection);
    connection.close();
    if (databases.isEmpty()) {
      printConnectionDetails(
          instanceConnectionName, Optional.empty(), optionalMysqlCredentials.get());
      return;
    }

    Optional<String> database = askForDatabase(databases);
    if (!database.isPresent()) {
      return;
    }

    printConnectionDetails(instanceConnectionName, database, optionalMysqlCredentials.get());
  }

  private List<String> listDatabases(Connection connection) throws SQLException {
    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery("SHOW DATABASES");
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

  private Optional<MysqlCredentials> askForMysqlCredentials(String instanceConnectionName)
      throws SQLException {
    Console console = System.console();
    String user;
    String lastUser = "root";
    for (; ; ) {
      char[] password;
      System.out.printf("Please enter MySQL username [%s]: ", lastUser);
      user = console.readLine();
      if (user == null) {
        return Optional.empty();
      }

      if (user.trim().isEmpty()) {
        user = lastUser;
      } else {
        lastUser = user;
      }

      System.out.print("Please enter MySQL password: ");
      password = console.readPassword();
      if (password == null) {
        return Optional.empty();
      }

      try {
        return Optional.of(
            new MysqlCredentials(
                user,
                password,
                DriverManager.getConnection(
                    constructJdbcUrl(instanceConnectionName, "mysql"),
                    user,
                    new String(password))));
      } catch (SQLException e) {
        if (e.getErrorCode() == 1045) {
          System.out.println("Invalid username/password. Please try again.");
          continue;
        }
        throw e;
      }
    }
  }

  private static String constructJdbcUrl(String instanceConnectionName, String database) {
    return String.format(
        "jdbc:mysql://google/%s?cloudSqlInstance=%s&"
            + "socketFactory=com.google.cloud.sql.mysql.SocketFactory",
        database,
        instanceConnectionName);
  }

  private Optional<String> askForInstance(List<DatabaseInstance> instances) {
    Optional<Integer> instanceChoice =
        chooseFromList(
            "Please enter the number of the instance you want to use [1]: ",
            instances.stream()
                .map(inst -> String.format("%s (%s)", inst.getName(), inst.getConnectionName()))
                .collect(Collectors.toList()));
    if (!instanceChoice.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(instances.get(instanceChoice.get()).getConnectionName());
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
      String instanceConnectionName, Optional<String> database, MysqlCredentials mysqlCredentials) {
    String databaseName = database.orElse("<database_name>");

    System.out.println("\n\n");
    System.out.printf(
        "Use the following JDBC URL%s:\n\n    %s\n",
        !database.isPresent() ? " after creating a database" : "",
        constructJdbcUrl(instanceConnectionName, databaseName));
    System.out.println();
    System.out.println("    Username: " + mysqlCredentials.getUsername());
    System.out.println(
        "    Password: " + (mysqlCredentials.getPassword().length > 0 ? "<yes>" : "<empty>"));
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

  private static final class MysqlCredentials {
    private final String username;
    private final char[] password;
    private final Connection connection;

    public MysqlCredentials(String username, char[] password, Connection connection) {
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

  public static void main(String[] args) throws IOException, SQLException {
    GettingStarted gettingStarted = new GettingStarted();
    new JCommander(gettingStarted, args);
    gettingStarted.run();
  }
}
