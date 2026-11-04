/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.storage.database;

import com.djrapitops.plan.exceptions.database.DBInitException;
import com.djrapitops.plan.exceptions.database.DBOpException;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.PluginLang;
import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import dagger.Lazy;
import dev.vankka.dependencydownload.ApplicationDependencyManager;
import net.playeranalytics.plugin.scheduling.RunnableFactory;
import net.playeranalytics.plugin.server.PluginLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL database implementation.
 * <p>
 * Mirrors {@link MySQLDB}: the JDBC driver is downloaded at runtime into an isolated class loader
 * and connections are served from a HikariCP pool.
 *
 * @author Plan fork
 */
@Singleton
public class PostgreSQLDB extends SQLDB {

    private static final String DRIVER_CLASS_NAME = "org.postgresql.Driver";
    private static final String DEFAULT_LAUNCH_OPTIONS = "?reWriteBatchedInserts=true&sslmode=disable";

    private static int increment = 1;

    protected HikariDataSource dataSource;

    @Inject
    public PostgreSQLDB(
            Locale locale,
            PlanConfig config,
            PlanFiles files,
            Lazy<ServerInfo> serverInfo,
            RunnableFactory runnableFactory,
            PluginLogger pluginLogger,
            ErrorLogger errorLogger,
            ApplicationDependencyManager applicationDependencyManager,
            Processing processing
    ) {
        super(
                () -> serverInfo.get().getServerUUID(),
                locale,
                config,
                files,
                runnableFactory,
                pluginLogger,
                errorLogger,
                applicationDependencyManager,
                processing
        );
    }

    private static synchronized void increment() {
        increment++;
    }

    @Override
    public DBType getType() {
        return DBType.POSTGRESQL;
    }

    @Override
    protected List<String> getDependencyResource() {
        try {
            return files.getResourceFromJar("dependencies/postgresqlDriver.txt").asLines();
        } catch (IOException e) {
            throw new DBInitException("Failed to get PostgreSQL dependency information", e);
        }
    }

    /**
     * Setups the {@link HikariDataSource}
     */
    @Override
    public void setupDataSource() {
        if (driverClassLoader == null) {
            logger.info(locale.getString(PluginLang.DB_DOWNLOAD_DRIVER, "PostgreSQL"));
            downloadDriver();
        }

        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();

        // Set the context class loader to the driver class loader for Hikari to use for finding the Driver
        currentThread.setContextClassLoader(driverClassLoader);

        try {
            loadDataSource();
        } finally {
            // Reset the context classloader back to what it was originally set to, now that the DataSource is created
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private void loadDataSource() {
        try {
            HikariConfig hikariConfig = new HikariConfig();

            String host = config.get(DatabaseSettings.POSTGRESQL_HOST);
            String port = config.get(DatabaseSettings.POSTGRESQL_PORT);
            String database = config.get(DatabaseSettings.POSTGRESQL_DATABASE);
            String launchOptions = config.get(DatabaseSettings.POSTGRESQL_LAUNCH_OPTIONS);
            // REGEX: match "?", match "word=word&" *-times, match "word=word"

            if (launchOptions.isEmpty() || !launchOptions.matches("\\?((([\\w-])+=.+)&)*(([\\w-])+=.+)")) {
                launchOptions = DEFAULT_LAUNCH_OPTIONS;
                logger.error(locale.getString(PluginLang.DB_MYSQL_LAUNCH_OPTIONS_FAIL, launchOptions));
            }
            hikariConfig.setDriverClassName(DRIVER_CLASS_NAME);
            hikariConfig.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database + launchOptions);

            String username = config.get(DatabaseSettings.POSTGRESQL_USER);
            String password = config.get(DatabaseSettings.POSTGRESQL_PASS);

            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            // Keep timestamps in UTC like the MySQL setup does with 'set time_zone = +00:00'
            hikariConfig.addDataSourceProperty("connectionInitSql", "SET TIME ZONE 'UTC'");

            hikariConfig.setPoolName("Plan Connection Pool-" + increment);
            increment();

            hikariConfig.setAutoCommit(false);
            setMaxConnections(hikariConfig);
            long maxLifetime = config.getOrDefault(DatabaseSettings.POSTGRESQL_MAX_LIFETIME, TimeUnit.MINUTES.toMillis(25L));
            hikariConfig.setMaxLifetime(maxLifetime);
            hikariConfig.setLeakDetectionThreshold(maxLifetime + TimeUnit.SECONDS.toMillis(4L));

            this.dataSource = new HikariDataSource(hikariConfig);
        } catch (HikariPool.PoolInitializationException e) {
            throw new DBInitException("Failed to set-up HikariCP Datasource: " + e.getMessage(), e);
        } finally {
            unloadPostgreSQLDriver();
        }
    }

    private void setMaxConnections(HikariConfig hikariConfig) {
        try {
            hikariConfig.setMaximumPoolSize(config.get(DatabaseSettings.POSTGRESQL_MAX_CONNECTIONS));
        } catch (IllegalStateException e) {
            logger.warn(e.getMessage() + ", using 1 as maximum for now.");
            hikariConfig.setMaximumPoolSize(1);
        }
    }

    private void unloadPostgreSQLDriver() {
        // Avoid issues with other plugins by removing the postgresql driver from driver manager
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            Class<?> driverClass = driver.getClass();
            // Checks that it's from our class loader to avoid unloading another plugin's/the server's driver
            if (DRIVER_CLASS_NAME.equals(driverClass.getName()) && driverClass.getClassLoader() == driverClassLoader) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        if (!connection.isValid(5)) {
            connection.close();
            throw new DBOpException("Valid connection could not be fetched (Is PostgreSQL down?)");
        }
        if (connection.getAutoCommit()) connection.setAutoCommit(false);
        setTimezoneToUTC(connection);
        return connection;
    }

    private void setTimezoneToUTC(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE 'UTC'");
        }
    }

    @Override
    public void close() {
        super.close();

        if (dataSource != null) dataSource.close();
    }

    @Override
    public void returnToPool(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            errorLogger.error(e, ErrorContext.builder().related("Closing connection").build());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PostgreSQLDB that = (PostgreSQLDB) o;
        return Objects.equals(dataSource, that.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSource);
    }
}
