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

import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.locale.Locale;
import net.playeranalytics.plugin.server.PluginLogger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bungee Database system that initializes MySQL object.
 *
 * @author AuroraLS3
 */
@Singleton
public class ProxyDBSystem extends DBSystem {

    @Inject
    public ProxyDBSystem(
            PlanConfig config,
            Locale locale,
            MySQLDB mySQLDB,
            PostgreSQLDB postgreSQLDB,
            SQLiteDB.Factory sqLiteDB,
            PluginLogger logger
    ) {
        super(config, locale, sqLiteDB, logger);
        databases.add(mySQLDB);
        databases.add(postgreSQLDB);
        db = mySQLDB;
    }

    @Override
    public void enable() {
        // Proxy config has no Database.Type by default - MySQL is used unless another registered type is configured
        String dbType = config.getOrDefault(DatabaseSettings.TYPE, "MySQL").toLowerCase().trim();
        if (DBType.exists(dbType)) {
            db = getActiveDatabaseByName(dbType);
        }
        super.enable();
    }
}
