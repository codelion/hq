/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */
package org.hyperic.hq.plugin.postgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.JDBCMeasurementPlugin;
import org.hyperic.hq.product.Metric;

public class PostgreSQLMeasurementPlugin extends JDBCMeasurementPlugin {

    protected static final String JDBC_DRIVER = "org.postgresql.Driver";
    private static Log log = LogFactory.getLog(PostgreSQLMeasurementPlugin.class);

    @Override
    protected void getDriver() throws ClassNotFoundException {
        Class.forName(JDBC_DRIVER);
    }

    @Override
    protected Connection getConnection(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    protected String getDefaultURL() {
        return null;
    }

    @Override
    protected void initQueries() {
    }

    @Override
    protected String getQuery(Metric metric) {
        String type = metric.getObjectProperties().getProperty("Type");
        String query = null;

        if (type.equalsIgnoreCase("Server")) {
            query = getServerQuery(metric);
        } else if (type.equalsIgnoreCase("Table")) {
            query = getTableQuery(metric);
        } else if (type.equalsIgnoreCase("Index")) {
            query = getIndexQuery(metric);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("[getQuery] query=" + query);
        }
        
        return query;
    }

    private String getIndexQuery(Metric metric) {
        String indexName = metric.getObjectProperties().getProperty(PostgreSQL.PROP_INDEX);
        String schemaName = metric.getObjectProperties().getProperty(PostgreSQL.PROP_SCHEMA);
        String attributeName = metric.getAttributeName();
        if (metric.isAvail()) {
            attributeName = "idx_scan";
        }
        String indexQuery = "SELECT " + attributeName + " FROM pg_stat_user_indexes "
                + "WHERE indexrelname='" + indexName + "' "
                + "AND schemaname='" + schemaName + "'";
        return indexQuery;
    }

    private String getTableQuery(Metric metric) {
        String tableName = metric.getObjectProperties().getProperty(PostgreSQL.PROP_TABLE);
        String schemaName = metric.getObjectProperties().getProperty(PostgreSQL.PROP_SCHEMA);
        String attributeName = metric.getAttributeName();
        if (metric.isAvail()) {
            attributeName = "seq_scan";
        }
        String tableQuery = null;
        if (attributeName.equals("DataSpaceUsed")) {
            tableQuery = "SELECT SUM(relpages) * 8 FROM pg_class "
                    + "JOIN pg_catalog.pg_namespace n ON n.oid = pg_class.relnamespace "
                    + "WHERE pg_class.relname = '" + tableName + "' "
                    + "AND n.nspname ='" + schemaName + "'";
        } else if (attributeName.equals("IndexSpaceUsed")) {
            tableQuery = "SELECT SUM(relpages) * 8 FROM pg_class "
                    + "JOIN pg_catalog.pg_namespace n ON n.oid = pg_class.relnamespace "
                    + "WHERE n.nspname = '" + schemaName + "' "
                    + "AND relname IN (SELECT indexrelname FROM "
                    + "pg_stat_user_indexes WHERE relname='"
                    + tableName + "' AND schemaname='" + schemaName + "')";
        } else {
            // Else normal query from pg_stat_user_table
            tableQuery = "SELECT " + attributeName + " FROM pg_stat_user_tables "
                    + "WHERE relname='" + tableName + "' "
                    + "AND schemaname='" + schemaName + "'";
        }
        return tableQuery;
    }

    private String getServerQuery(Metric metric) {
        String attributeName = metric.getAttributeName();
        if (metric.isAvail()) {
            attributeName = "numbackends";
        }

        String db = metric.getObjectProperty(PostgreSQL.PROP_DFDB);

        String serverQuery = null;
        // Check metrics that require joins across tables.
        if (attributeName.equals("LocksHeld")) {
            serverQuery = "SELECT COUNT(*) FROM PG_STAT_DATABASE, PG_LOCKS "
                    + "WHERE PG_LOCKS.DATABASE = PG_STAT_DATABASE.DATID AND "
                    + "PG_STAT_DATABASE.DATNAME = '" + db + "'";
        } else if (attributeName.equals("DataSpaceUsed")) {
            // XXX assumes 8k page size. (which is the default)
            serverQuery = "SELECT SUM(relpages) * 8 FROM pg_class WHERE "
                    + "relname IN (SELECT relname from pg_stat_user_tables)";
        } else if (attributeName.equals("IndexSpaceUsed")) {
            serverQuery = "SELECT SUM(relpages) * 8 FROM pg_class WHERE "
                    + "relname IN (SELECT indexrelname from "
                    + "pg_stat_user_indexes)";
        } else {
            // Else normal query from pg_stat_database
            serverQuery = "SELECT " + attributeName + " FROM pg_stat_database "
                    + "WHERE datname='" + db + "'";
        }
        return serverQuery;
    }
}
