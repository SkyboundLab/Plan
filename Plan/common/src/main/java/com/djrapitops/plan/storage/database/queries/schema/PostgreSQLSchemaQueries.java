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
package com.djrapitops.plan.storage.database.queries.schema;

import com.djrapitops.plan.storage.database.queries.HasMoreThanZeroQueryStatement;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import org.intellij.lang.annotations.Language;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Static method class for PostgreSQL Schema related queries.
 * <p>
 * PostgreSQL specific schema queries live here so that adding a new database type
 * does not require changes to the shared query/transaction classes.
 *
 * @author Plan fork
 */
public class PostgreSQLSchemaQueries {

    private PostgreSQLSchemaQueries() {
        /* Static method class */
    }

    public static Query<Optional<String>> getVersion() {
        @Language("PostgreSQL")
        String sql = "SELECT version()";
        return db -> db.queryOptional(sql, row -> row.getString(1));
    }

    public static Query<Boolean> doesTableExist(String tableName) {
        String sql = SELECT + "COUNT(1) as c FROM information_schema.TABLES" +
                WHERE + "table_name=? AND table_schema=current_schema() AND table_type='BASE TABLE'";
        return new HasMoreThanZeroQueryStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, tableName);
            }
        };
    }

    public static Query<Boolean> doesColumnExist(String tableName, String columnName) {
        String sql = SELECT + "COUNT(1) as c FROM information_schema.COLUMNS" +
                WHERE + "table_name=? AND column_name=? AND table_schema=current_schema()";
        return new HasMoreThanZeroQueryStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, tableName);
                statement.setString(2, columnName);
            }
        };
    }

    public static Query<Boolean> doesIndexExist(String indexName, String tableName) {
        String sql = SELECT + "COUNT(1) as c FROM pg_indexes" +
                WHERE + "schemaname=current_schema() AND tablename=? AND indexname=?";
        return new HasMoreThanZeroQueryStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, tableName);
                statement.setString(2, indexName);
            }
        };
    }

    public static Query<Boolean> doesUniqueIndexExist(String indexName, String tableName) {
        String sql = SELECT + "COUNT(1) as c FROM pg_indexes i" +
                INNER_JOIN + "pg_class c ON c.relname = i.indexname AND c.relnamespace = current_schema()::regnamespace" +
                INNER_JOIN + "pg_index x ON x.indexrelid = c.oid" +
                WHERE + "i.schemaname=current_schema() AND i.tablename=? AND i.indexname=? AND x.indisunique";
        return new HasMoreThanZeroQueryStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, tableName);
                statement.setString(2, indexName);
            }
        };
    }

    public static Query<Integer> columnVarcharLength(String table, String column) {
        String sql = SELECT + "CHARACTER_MAXIMUM_LENGTH" +
                FROM + "information_schema.COLUMNS" +
                WHERE + "table_name=? AND column_name=? AND table_schema=current_schema()";

        return new QueryStatement<>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, table);
                statement.setString(2, column);
            }

            @Override
            public Integer processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getInt("CHARACTER_MAXIMUM_LENGTH") : Integer.MAX_VALUE;
            }
        };
    }

    /**
     * Lists single-column foreign key constraints that reference the given table.
     * <p>
     * Returns the same {@link MySQLSchemaQueries.ForeignKeyConstraint} type as the MySQL variant
     * so that shared patch code can consume both.
     */
    public static Query<List<MySQLSchemaQueries.ForeignKeyConstraint>> foreignKeyConstraintsOf(String referencedTable) {
        String keySQL = SELECT + "con.conname AS constraint_name, cl.relname AS table_name, att2.attname AS column_name," +
                "cl2.relname AS referenced_table_name, att.attname AS referenced_column_name" +
                FROM + "pg_catalog.pg_constraint con" +
                INNER_JOIN + "pg_catalog.pg_class cl ON con.conrelid = cl.oid" +
                INNER_JOIN + "pg_catalog.pg_class cl2 ON con.confrelid = cl2.oid" +
                INNER_JOIN + "pg_catalog.pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = con.confkey[1]" +
                INNER_JOIN + "pg_catalog.pg_attribute att2 ON att2.attrelid = con.conrelid AND att2.attnum = con.conkey[1]" +
                WHERE + "con.contype = 'f'" +
                AND + "cl.relnamespace = current_schema()::regnamespace" +
                AND + "cl2.relname = ?";
        return new QueryStatement<>(keySQL) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, referencedTable);
            }

            @Override
            public List<MySQLSchemaQueries.ForeignKeyConstraint> processResults(ResultSet set) throws SQLException {
                List<MySQLSchemaQueries.ForeignKeyConstraint> constraints = new ArrayList<>();

                while (set.next()) {
                    constraints.add(new MySQLSchemaQueries.ForeignKeyConstraint(
                            set.getString("table_name"),
                            set.getString("referenced_table_name"),
                            set.getString("column_name"),
                            set.getString("referenced_column_name"),
                            set.getString("constraint_name")
                    ));
                }

                return constraints;
            }
        };
    }
}
