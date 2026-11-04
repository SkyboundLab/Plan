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
package utilities;

/**
 * Test Utility class that holds System property names used in Travis.
 *
 * @author AuroraLS3
 */
public class CIProperties {

    public static final String MYSQL_DATABASE = "MYSQL_DB";
    // Notice to CI configurer: This user should only have local access.
    // (To prevent outside connections with CI user)
    public static final String MYSQL_USER = "MYSQL_USER";
    public static final String MYSQL_PASS = "MYSQL_PASS";
    public static final String MYSQL_PORT = "MYSQL_PORT";
    public static final String POSTGRES_DB = "POSTGRES_DB";
    public static final String POSTGRES_USER = "POSTGRES_USER";
    public static final String POSTGRES_PASS = "POSTGRES_PASS";
    public static final String POSTGRES_HOST = "POSTGRES_HOST";
    public static final String POSTGRES_PORT = "POSTGRES_PORT";
    public static final String CHROME_DRIVER = "CHROMEDRIVER";

    private CIProperties() {
        /* Static variable class */
    }
}
