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
package com.djrapitops.plan.storage.database.sql.building;

import com.djrapitops.plan.storage.database.DBType;
import org.apache.commons.text.TextStringBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Duplicate String reducing utility class for SQL language Strings.
 */
public abstract class Sql {
    public static final String ID = "id";
    public static final String P_UUID = "uuid";

    public static final String INT = "integer";
    public static final String DOUBLE = "double";
    public static final String LONG = "bigint";
    public static final String BOOL = "boolean";

    public static final String SELECT = "SELECT ";
    public static final String DISTINCT = "DISTINCT ";
    public static final String FROM = " FROM ";
    public static final String DELETE_FROM = "DELETE" + FROM;
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String UPDATE = "UPDATE ";
    public static final String SET = " SET ";
    public static final String WHERE = " WHERE ";
    public static final String GROUP_BY = " GROUP BY ";
    public static final String ORDER_BY = " ORDER BY ";
    public static final String INNER_JOIN = " JOIN ";
    public static final String JOIN = INNER_JOIN;
    public static final String LEFT_JOIN = " LEFT JOIN ";
    public static final String UNION = " UNION ";
    public static final String UNION_ALL = " UNION ALL ";
    public static final String AND = " AND ";
    public static final String OR = " OR ";
    public static final String IS_NULL = " IS NULL";
    public static final String IS_NOT_NULL = " IS NOT NULL";
    public static final String LIMIT = " LIMIT ";
    public static final String LIMIT_1 = " LIMIT 1 ";
    public static final String OFFSET = " OFFSET ";
    public static final String TEXT = "TEXT";

    private static final String FLOOR = "FLOOR(";
    private static final String AVG = "AVG(";
    private static final String MIN = "MIN(";
    private static final String MAX = "MAX(";
    private static final String SUM = "SUM(";
    private static final String VARCHAR = "varchar(";

    public static String nParameters(int n) {
        return new TextStringBuilder()
                .appendWithSeparators(IntStream.range(0, n).mapToObj(i -> "?").iterator(), ",")
                .toString();
    }

    public static String varchar(int length) {
        return VARCHAR + length + ')';
    }

    public static String floor(String expression) {return FLOOR + expression + ')';}

    public static String sum(String expression) {return SUM + expression + ')';}

    public static String min(String expression) {return MIN + expression + ')';}

    public static String max(String expression) {return MAX + expression + ')';}

    public static String avg(String expression) {return AVG + expression + ')';}

    /**
     * Turn day of week to epoch ms.
     * <p>
     * 1st of January 1970 (Epoch) is Thursday (-2).
     *
     * @param day 1 = Sunday, 2 = Monday etc.. 7 = Saturday
     * @return Milliseconds since epoch for this day to be given by {@link java.text.SimpleDateFormat} "EEEE"
     */
    public static long getDayEpochMs(int day) {
        return TimeUnit.DAYS.toMillis(day + 2L);
    }

    public static void setStringOrNull(PreparedStatement statement, int index, String value) throws SQLException {
        if (value != null) {
            statement.setString(index, value);
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    public static void setDoubleOrNull(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value != null) {
            statement.setDouble(index, value);
        } else {
            statement.setNull(index, Types.DOUBLE);
        }
    }

    public static void setIntOrNull(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value != null) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    public static String concat(DBType dbType, String one, String two) {
        return dbType.getSql().concat(one, two);
    }

    /**
     * Dialect specific string concatenation expression.
     * <p>
     * Defaults to the SQL standard {@code ||} operator (SQLite, PostgreSQL),
     * MySQL overrides this with {@code CONCAT(..)}.
     */
    public String concat(String one, String two) {
        return one + " || " + two;
    }

    /**
     * Maps a generic column type (see {@link #INT}, {@link #DOUBLE} etc.) to a dialect specific column type.
     * <p>
     * Defaults to the type as-is.
     */
    public String columnType(String type) {
        return type;
    }

    public static Double getDoubleOrNull(ResultSet set, String column) throws SQLException {
        double value = set.getDouble(column);
        return set.wasNull() ? null : value;
    }

    public static Integer getIntOrNull(ResultSet set, String column) throws SQLException {
        int value = set.getInt(column);
        return set.wasNull() ? null : value;
    }

    public abstract String epochSecondToDate(String sql);

    public abstract String dateToEpochSecond(String sql);

    public abstract String dateToDayStamp(String sql);

    public abstract String dateToHourStamp(String sql);

    public abstract String dateToDayOfWeek(String sql);

    public abstract String dateToHour(String sql);

    public abstract String insertOrIgnore();

    /**
     * Suffix appended after an {@link #insertOrIgnore()} statement.
     * <p>
     * Needed by dialects where "insert or ignore" can not be expressed as a statement prefix
     * (PostgreSQL uses {@code ON CONFLICT DO NOTHING} at the end of the statement).
     */
    public String insertOrIgnoreSuffix() {
        return "";
    }

    public abstract String least(String values);

    public abstract String greatest(String values);

    /**
     * SQL condition matching rows where the given column contains a null character (U+0000).
     * <p>
     * Defaults to {@code INSTR(column, CHAR(0))} (MySQL, SQLite).
     * PostgreSQL overrides this with {@code FALSE} since text columns can not contain U+0000.
     */
    public String containsNullChar(String column) {
        return "INSTR(" + column + ", CHAR(0))";
    }

    /**
     * ORDER BY clause for a text column with byte-wise (binary) ordering.
     * <p>
     * Defaults to a plain {@code ORDER BY column} (MySQL, SQLite).
     * PostgreSQL overrides this with {@code COLLATE "C"} since a database created with a locale
     * collation (e.g. en_US.utf8) orders punctuation and case differently than MySQL/SQLite.
     */
    public String orderByText(String column) {
        return ORDER_BY + column;
    }

    /**
     * Aggregates distinct values of an expression into a comma separated string.
     * <p>
     * Defaults to {@code GROUP_CONCAT(DISTINCT ..)} (MySQL, SQLite),
     * PostgreSQL overrides this with {@code string_agg}.
     */
    public String groupConcatDistinct(String expr) {
        return "GROUP_CONCAT(DISTINCT " + expr + ")";
    }

    /**
     * Aggregates values of an expression into a string separated by the given SQL string literal.
     * <p>
     * Defaults to {@code GROUP_CONCAT(.., sep)} (MySQL, SQLite),
     * PostgreSQL overrides this with {@code string_agg}.
     */
    public String groupConcat(String expr, String separator) {
        return "GROUP_CONCAT(" + expr + ',' + separator + ")";
    }

    public String lockForUpdate() {
        return "";
    }

    /**
     * Translates a raw SQL string written for another database dialect into this dialect's syntax.
     * <p>
     * Used for raw SQL coming from external sources (e.g. extensions) that can not use the
     * dialect aware query builders. Defaults to returning the SQL unchanged.
     *
     * @param sql Raw SQL string
     * @return SQL translated to this dialect
     */
    public String translateRawSQL(String sql) {
        return sql;
    }

    // https://dev.mysql.com/doc/refman/5.7/en/date-and-time-functions.html
    public static class MySQL extends Sql {

        @Override
        public String epochSecondToDate(String sql) {
            return "FROM_UNIXTIME(" + sql + ')';
        }

        @Override
        public String dateToEpochSecond(String sql) {
            return "UNIX_TIMESTAMP(" + sql + ')';
        }

        @Override
        public String dateToDayStamp(String sql) {
            return "DATE(" + sql + ')';
        }

        @Override
        public String dateToHourStamp(String sql) {
            return "DATE_FORMAT(" + sql + ",'%Y-%m-%d %H:00:00')";
        }

        @Override
        public String dateToDayOfWeek(String sql) {
            return "DAYOFWEEK(" + sql + ')';
        }

        @Override
        public String dateToHour(String sql) {
            return "HOUR(" + sql + ") % 24";
        }

        @Override
        public String insertOrIgnore() {
            return "INSERT IGNORE INTO ";
        }

        @Override
        public String least(String values) {
            return "LEAST(" + values + ")";
        }

        @Override
        public String greatest(String values) {
            return "GREATEST(" + values + ")";
        }

        @Override
        public String lockForUpdate() {
            return " FOR UPDATE";
        }

        @Override
        public String concat(String one, String two) {
            return "CONCAT(" + one + ',' + two + ")";
        }
    }

    // https://sqlite.org/lang_datefunc.html
    public static class SQLite extends Sql {

        @Override
        public String epochSecondToDate(String sql) {
            return "datetime(" + sql + ", 'unixepoch')";
        }

        @Override
        public String dateToEpochSecond(String sql) {
            return "strftime('%s'," + sql + ")";
        }

        @Override
        public String dateToDayStamp(String sql) {
            return "strftime('%Y-%m-%d'," + sql + ')';
        }

        @Override
        public String dateToHourStamp(String sql) {
            return "strftime('%Y-%m-%d %H:00:00'," + sql + ')';
        }

        @Override
        public String dateToDayOfWeek(String sql) {
            return "strftime('%w'," + sql + ")+1";
        }

        @Override
        public String dateToHour(String sql) {
            return "strftime('%H'," + sql + ')';
        }

        @Override
        public String insertOrIgnore() {
            return "INSERT OR IGNORE INTO ";
        }

        @Override
        public String least(String values) {
            return "MIN(" + values + ")";
        }

        @Override
        public String greatest(String values) {
            return "MAX(" + values + ")";
        }
    }

    // https://www.postgresql.org/docs/current/functions-datetime.html
    public static class PostgreSQL extends Sql {

        private static final Pattern AUTO_INCREMENT = Pattern.compile("\\bAUTO_INCREMENT\\b", Pattern.CASE_INSENSITIVE);

        @Override
        public String epochSecondToDate(String sql) {
            return "to_timestamp(" + sql + ')';
        }

        @Override
        public String dateToEpochSecond(String sql) {
            return "CAST(EXTRACT(EPOCH FROM " + sql + ") AS BIGINT)";
        }

        @Override
        public String dateToDayStamp(String sql) {
            return "CAST(" + sql + " AS DATE)";
        }

        @Override
        public String dateToHourStamp(String sql) {
            return "date_trunc('hour'," + sql + ')';
        }

        @Override
        public String dateToDayOfWeek(String sql) {
            // DOW: 0 = Sunday .. 6 = Saturday, +1 to match MySQL DAYOFWEEK (1 = Sunday .. 7 = Saturday)
            return "CAST(EXTRACT(DOW FROM " + sql + ") AS INTEGER) + 1";
        }

        @Override
        public String dateToHour(String sql) {
            return "CAST(EXTRACT(HOUR FROM " + sql + ") AS INTEGER)";
        }

        @Override
        public String insertOrIgnore() {
            return "INSERT INTO ";
        }

        @Override
        public String insertOrIgnoreSuffix() {
            return " ON CONFLICT DO NOTHING";
        }

        @Override
        public String least(String values) {
            return "LEAST(" + values + ")";
        }

        @Override
        public String greatest(String values) {
            return "GREATEST(" + values + ")";
        }

        @Override
        public String groupConcatDistinct(String expr) {
            return "string_agg(DISTINCT " + expr + ", ',')";
        }

        @Override
        public String groupConcat(String expr, String separator) {
            return "string_agg(" + expr + ", " + separator + ")";
        }

        // PostgreSQL does not allow FOR UPDATE with aggregate functions, GROUP BY or DISTINCT,
        // which several queries combine it with - so no row locking is used (base implementation).
        // Concurrent writes are handled by unique constraints and SQLState based retries instead.

        @Override
        public String containsNullChar(String column) {
            // PostgreSQL text columns can not contain U+0000, and CHAR(0) is not valid SQL there
            return "FALSE";
        }

        @Override
        public String orderByText(String column) {
            // Byte-wise ordering to match MySQL/SQLite regardless of the database's locale collation
            return ORDER_BY + column + " COLLATE \"C\"";
        }

        @Override
        public String columnType(String type) {
            // PostgreSQL does not have a type called "double"
            if (DOUBLE.equals(type)) {
                return "double precision";
            }
            return type;
        }

        @Override
        public String translateRawSQL(String sql) {
            // Raw SQL written for MySQL (e.g. by extensions) uses AUTO_INCREMENT and backtick
            // quoting, neither of which is valid PostgreSQL syntax.
            return AUTO_INCREMENT.matcher(sql)
                    .replaceAll(Matcher.quoteReplacement("GENERATED BY DEFAULT AS IDENTITY"))
                    .replace('`', '"');
        }
    }
}
