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

import com.djrapitops.plan.SubSystem;
import com.djrapitops.plan.exceptions.EnableException;
import com.djrapitops.plan.exceptions.database.DBInitException;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.PluginLang;
import net.playeranalytics.plugin.server.PluginLogger;
import org.jetbrains.annotations.NotNull;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * System that holds the active databases.
 *
 * @author AuroraLS3
 */
@Singleton
public class DBSystem implements SubSystem {

    protected final PlanConfig config;
    protected final Locale locale;
    private final SQLiteDB.Factory sqLiteFactory;
    protected final PluginLogger logger;

    protected Database db;
    protected final Set<Database> databases;

    public DBSystem(
            PlanConfig config,
            Locale locale,
            SQLiteDB.Factory sqLiteDB,
            PluginLogger logger
    ) {
        this.config = config;
        this.locale = locale;
        this.sqLiteFactory = sqLiteDB;
        this.logger = logger;
        databases = new HashSet<>();
    }

    public Database getActiveDatabaseByName(String dbName) {
        if ("h2".equalsIgnoreCase(dbName)) {
            throw new EnableException("H2 database is NO LONGER SUPPORTED. Downgrade to 5.3 build 1284 and migrate to SQLite or MySQL using '/plan db move h2 <db>' command");
        }
        return DBType.getForName(dbName)
                .map(this::getActiveDatabaseByType)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(PluginLang.ENABLE_FAIL_WRONG_DB, dbName)));
    }

    public Database getActiveDatabaseByType(DBType type) {
        for (Database database : getDatabases()) {
            if (database.getType() == type) {
                return database;
            }
        }
        throw new IllegalArgumentException(locale.getString(PluginLang.ENABLE_FAIL_WRONG_DB, type != null ? type.getName() : "null"));
    }

    public Set<Database> getDatabases() {
        return databases;
    }

    @Override
    public void disable() {
        if (db != null) {
            db.close();
        }
    }

    public Database getDatabase() {
        return db;
    }

    @Override
    public void enable() {
        try {
            db.init();
            logger.info(locale.getString(PluginLang.ENABLED_DATABASE, db.getType().getName()));
        } catch (DBInitException e) {
            Throwable cause = e.getCause();
            String message = cause == null ? e.getMessage() : cause.getMessage();
            if (db.getType() == DBType.POSTGRESQL && isConnectionFailure(cause)) {
                throw new EnableException(getPostgreSQLConnectionFailureMessage(), cause);
            } else if (message != null && message.contains("The driver has not received any packets from the server.")) {
                throw new EnableException(getMySQLConnectionFailureMessage());
            } else {
                throw new EnableException("Failed to start " + db.getType().getName() + ": " + message, cause);
            }
        }
    }

    /**
     * Checks if the failure was caused by not being able to connect to the database server.
     * PostgreSQL reports connection problems with SQLState class 08 (connection exception).
     */
    private static boolean isConnectionFailure(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException) {
                String sqlState = ((SQLException) t).getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Connection refused")
                    || msg.contains("could not connect")
                    || msg.contains("The connection attempt failed")
                    || msg.contains("Connection timed out"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    @NotNull
    private String getPostgreSQLConnectionFailureMessage() {
        return "Failed to start " + db.getType().getName() + ": Could not connect to PostgreSQL-" +
                "\n- Check that database address '" + config.get(DatabaseSettings.POSTGRESQL_HOST) + ":" + config.get(DatabaseSettings.POSTGRESQL_PORT) + "' is accessible." +
                "\n- Check that database called '" + config.get(DatabaseSettings.POSTGRESQL_DATABASE) + "' exists inside PostgreSQL." +
                "\n- Check that PostgreSQL user '" + config.get(DatabaseSettings.POSTGRESQL_USER) + "' has privileges to access the database." +
                "\n- Check that other PostgreSQL settings in Plan config correct (sslmode can be set with Launch_options)." +
                (isInsideDocker() ? "\n- Check that your docker container networking is set up correctly (Since your server is running inside a docker)" : "") +
                "\n  More help: https://github.com/plan-player-analytics/Plan/wiki/Bungee-Set-Up#step-2-create-a-mysql-database-for-plan";
    }

    @NotNull
    private String getMySQLConnectionFailureMessage() {
        return "Failed to start " + db.getType().getName() + ": Communications link failure. Plan could not connect to MySQL-" +
                "\n- Check that database address '" + config.get(DatabaseSettings.MYSQL_HOST) + ":" + config.get(DatabaseSettings.MYSQL_PORT) + "' accessible." +
                "\n- Check that database called '" + config.get(DatabaseSettings.MYSQL_DATABASE) + "' exists inside MySQL." +
                "\n- Check that MySQL user '" + config.get(DatabaseSettings.MYSQL_USER) + "' has privileges to access the database." +
                "\n- Check that other MySQL settings in Plan config correct." +
                (isInsideDocker() ? "\n- Check that your docker container networking is set up correctly https://pterodactyl.io/tutorials/mysql_setup.html (Since your server is running inside a docker)" : "") +
                "\n  More help: https://github.com/plan-player-analytics/Plan/wiki/Bungee-Set-Up#step-2-create-a-mysql-database-for-plan";
    }

    private boolean isInsideDocker() {
        try (Stream<String> stream = Files.lines(Paths.get("/proc/1/cgroup"))) {
            return stream.anyMatch(line -> line.contains("/docker"));
        } catch (IOException | InvalidPathException | SecurityException e) {
            return false;
        }
    }

    public void setActiveDatabase(Database db) {
        this.db.close();
        this.db = db;
    }

    public SQLiteDB.Factory getSqLiteFactory() {
        return sqLiteFactory;
    }

}
