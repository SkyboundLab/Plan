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

import com.djrapitops.plan.component.ComponentSvc;
import com.djrapitops.plan.delivery.DeliveryUtilities;
import com.djrapitops.plan.extension.ExtensionSvc;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.storage.database.queries.ExtensionsDatabaseTest;
import com.djrapitops.plan.storage.database.queries.filter.QueryFilters;
import com.djrapitops.plan.storage.database.transactions.StoreServerInformationTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemoveEverythingTransaction;
import com.djrapitops.plan.storage.database.transactions.init.CreateIndexTransaction;
import com.djrapitops.plan.storage.database.transactions.init.CreateTablesTransaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import utilities.DBPreparer;
import utilities.RandomData;
import utilities.TestConstants;
import utilities.TestErrorLogger;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for PostgreSQL database.
 * <p>
 * The setup assumes CI environment with PostgreSQL service running.
 * 'POSTGRES_DB' database should be created before the test.
 *
 * @author Plan fork
 * @see DatabaseTest
 * @see ExtensionsDatabaseTest
 * @see utilities.CIProperties for assumed PostgreSQL setup.
 */
@ExtendWith(MockitoExtension.class)
class PostgreSQLTest implements DatabaseTest, DatabaseTestAggregate {

    private static final int TEST_PORT_NUMBER = RandomData.randomInt(9005, 9500);

    private static Database database;
    private static DatabaseTestComponent component;
    private static DBPreparer preparer;

    @BeforeAll
    static void setupDatabase(@TempDir Path temp) {
        component = DaggerDatabaseTestComponent.builder()
                .bindTemporaryDirectory(temp)
                .build();
        preparer = new DBPreparer(component, TEST_PORT_NUMBER);
        Optional<Database> postgreSQL = preparer.preparePostgreSQL();
        Assumptions.assumeTrue(postgreSQL.isPresent());
        database = postgreSQL.get();
        database.executeTransaction(new CreateTablesTransaction()).join();
        database.executeTransaction(new CreateIndexTransaction()).join();
    }

    @AfterAll
    static void disableSystem() {
        preparer.preparePostgreSQL().ifPresent(Database::close);
        if (database != null) database.close();
        preparer.tearDown();
    }

    @BeforeEach
    void setUp() {
        TestErrorLogger.throwErrors(true);
        db().executeTransaction(new RemoveEverythingTransaction());

        db().executeTransaction(new StoreServerInformationTransaction(new Server(serverUUID(), TestConstants.SERVER_NAME, "", TestConstants.VERSION)));
        assertEquals(serverUUID(), ((SQLDB) db()).getServerUUIDSupplier().get());
    }

    @Override
    public Database db() {
        return database;
    }

    @Override
    public ServerUUID serverUUID() {
        return component.serverInfo().getServerUUID();
    }

    @Override
    public PlanConfig config() {
        return component.config();
    }

    @Override
    public DBSystem dbSystem() {
        return component.dbSystem();
    }

    @Override
    public ServerInfo serverInfo() {
        return component.serverInfo();
    }

    @Override
    public DeliveryUtilities deliveryUtilities() {
        return component.deliveryUtilities();
    }

    @Override
    public ExtensionSvc extensionService() {
        return component.extensionService();
    }

    @Override
    public ComponentSvc componentService() {
        return component.componentService();
    }

    @Override
    public QueryFilters queryFilters() {
        return component.queryFilters();
    }

    @Override
    public File dataFolder() {
        return component.files().getDataFolder();
    }

}
