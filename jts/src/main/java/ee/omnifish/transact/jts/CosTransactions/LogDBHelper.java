/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package ee.omnifish.transact.jts.CosTransactions;

import static ee.omnifish.transact.jts.CosTransactions.Configuration.DB_LOG_RESOURCE;
import static ee.omnifish.transact.jts.CosTransactions.GlobalTID.fromTIDBytes;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * The LogDBHelper class takes care of writing the transaction logs into database.
 *
 * @author Sun Micro Systems, Inc
 */
class LogDBHelper {

    private static Logger _logger = Logger.getLogger(LogDBHelper.class.getName());

    String resName = "jdbc/TxnDS";

    // serverName cannot be final - the instance is first requested
    // during auto-recovery when the server name is not yet final
    private String serverName;
    final private String instanceName;

    private DataSource dataSource;
    private Method getNonTxConnectionMethod;

    private static final String insertStatement = System.getProperty("ee.omnifish.transact.jts.dblogging.insertquery",
            "insert into  txn_log_table values ( ? , ? , ? , ? )");

    private static final String deleteStatement = System.getProperty("ee.omnifish.transact.jts.dblogging.deletequery",
            "delete from txn_log_table where localtid = ? and servername = ? ");

    private static final String selectStatement = System.getProperty("ee.omnifish.transact.jts.dblogging.selectquery",
            "select * from txn_log_table where servername = ? ");

    private static final String selectServerNameStatement = System.getProperty("ee.omnifish.transact.jts.dblogging.selectservernamequery",
            "select distinct servername from txn_log_table where instancename = ? ");

    private static final String createTableStatement = "create table txn_log_table (localtid varchar(20), servername varchar(150), instancename varchar(150), gtrid blob)";

    private static final boolean useNonTxConnectionForAddRecord = Boolean.getBoolean("ee.omnifish.transact.jts.dblogging.use.nontx.connection.for.add");

    private static LogDBHelper _instance = new LogDBHelper();

    static LogDBHelper getInstance() {
        return _instance;
    }

    LogDBHelper() {
        instanceName = Configuration.getPropertyValue(Configuration.INSTANCE_NAME);
        if (Configuration.getPropertyValue(DB_LOG_RESOURCE) != null) {
            resName = Configuration.getPropertyValue(DB_LOG_RESOURCE);
        }

        try {
            dataSource = (DataSource) InitialContext.doLookup(resName);
            getNonTxConnectionMethod = dataSource.getClass().getMethod("getNonTxConnection", null);

            createTable();

        } catch (Throwable t) {
            _logger.log(SEVERE, "jts.unconfigured_db_log_resource", resName);
            _logger.log(SEVERE, "", t);
        }

        if (_logger.isLoggable(FINE)) {
            _logger.fine("LogDBHelper.resName: " + resName);
            _logger.fine("LogDBHelper.ds: " + dataSource);
            _logger.fine("LogDBHelper.getNonTxConnectionMethod: " + getNonTxConnectionMethod);
        }
    }

    void setServerName() {
        // Add a mapping between the serverName and the instanceName
        String configuredServerName = Configuration.getServerName();
        if (serverName != null && serverName.equals(configuredServerName)) {
            // Nothing changed
            return;
        }

        serverName = configuredServerName;

        if (_logger.isLoggable(INFO)) {
            _logger.info("LogDBHelper.setServerName for serverName: " + serverName);
            _logger.info("LogDBHelper.setServerName for instanceName: " + instanceName);
        }

        String serverNameForInstanceName = getServerNameForInstanceName(instanceName);
        if (serverNameForInstanceName == null) {
            // Set the mapping
            _logger.info("LogDBHelper.initTable adding marker record...");
            addRecord(0, null);
        } else if (!serverNameForInstanceName.equals(serverName)) {
            // Override the mapping
            _logger.log(WARNING, "jts.exception_in_db_log_server_to_instance_mapping", new Object[] { instanceName, serverNameForInstanceName, serverName });
            deleteRecord(0, serverNameForInstanceName);
            addRecord(0, null);
        }
    }

    boolean addRecord(long localTID, byte[] data) {
        if (dataSource != null) {
            if (_logger.isLoggable(FINE)) {
                _logger.fine("LogDBHelper.addRecord for localTID: " + localTID);
                _logger.fine("LogDBHelper.addRecord for serverName: " + serverName);
                _logger.fine("LogDBHelper.addRecord for instanceName: " + instanceName);
            }

            Connection connection = null;
            PreparedStatement preparedStatement = null;
            try {
                if (useNonTxConnectionForAddRecord) {
                    connection = (Connection) (getNonTxConnectionMethod.invoke(dataSource, null));
                } else {
                    connection = dataSource.getConnection();
                }

                preparedStatement = connection.prepareStatement(insertStatement);
                preparedStatement.setString(1, Long.toString(localTID));
                preparedStatement.setString(2, serverName);
                preparedStatement.setString(3, instanceName);
                preparedStatement.setBytes(4, data);
                preparedStatement.executeUpdate();

                return true;
            } catch (Throwable ex) {
                _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex);
            } finally {
                tryClose(preparedStatement);
                tryClose(connection);
            }
        }

        return false;
    }

    boolean deleteRecord(long localTID) {
        return deleteRecord(localTID, serverName);
    }

    boolean deleteRecord(long localTID, String serverName0) {
        if (dataSource != null) {
            if (_logger.isLoggable(FINE)) {
                _logger.fine("LogDBHelper.deleteRecord for localTID: " + localTID + " and serverName: " + serverName0);
            }

            Connection connection = null;
            PreparedStatement preparedStatement = null;
            try {

                // To avoid compile time dependency to get NonTxConnection
                connection = (Connection) (getNonTxConnectionMethod.invoke(dataSource, null));
                preparedStatement = connection.prepareStatement(deleteStatement);
                preparedStatement.setString(1, Long.toString(localTID));
                preparedStatement.setString(2, serverName0); // Configuration.getServerName());
                preparedStatement.executeUpdate();

                return true;
            } catch (Exception ex) {
                _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex);
            } finally {
                tryClose(preparedStatement);
                tryClose(connection);
            }
        }

        return false;
    }

    Map<GlobalTID, Long> getGlobalTIDMap() {
        return getGlobalTIDMap(serverName);
    }

    Map<GlobalTID, Long> getGlobalTIDMap(String serverName) {
        Map<GlobalTID, Long> globalTIDMap = new HashMap<>();

        if (dataSource != null) {
            if (_logger.isLoggable(FINE)) {
                _logger.fine("LogDBHelper get records for serverName: " + serverName);
            }

            Connection connection = null;
            PreparedStatement preparedStatement = null;
            ResultSet resultSet = null;
            try {
                connection = (Connection) (getNonTxConnectionMethod.invoke(dataSource, null));
                preparedStatement = connection.prepareStatement(selectStatement);
                preparedStatement.setString(1, serverName);
                resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    Long localTID = Long.valueOf(resultSet.getString(1));
                    byte[] gtridbytes = resultSet.getBytes(4);
                    if (gtridbytes != null) {
                        // Skip mapping record
                        if (_logger.isLoggable(FINE)) {
                            _logger.fine("LogDBHelper found record for localTID: " + localTID + " and serverName: " + serverName);
                            _logger.fine("LogDBHelper GlobalTID for localTID: " + localTID + " : " + GlobalTID.fromTIDBytes(gtridbytes));
                        }

                        globalTIDMap.put(fromTIDBytes(gtridbytes), localTID);
                    }
                }
            } catch (Exception ex) {
                _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex);
            } finally {
                tryClose(resultSet);
                tryClose(preparedStatement);
                tryClose(connection);
            }
        }

        return globalTIDMap;
    }

    String getServerNameForInstanceName(String instanceName) {
        String serverName = null;

        if (dataSource != null) {
            if (_logger.isLoggable(FINE)) {
                _logger.fine("LogDBHelper get serverName for instanceName: " + instanceName);
            }

            Connection connection = null;
            PreparedStatement preparedStatement = null;
            ResultSet resultSet = null;
            try {
                connection = (Connection) (getNonTxConnectionMethod.invoke(dataSource, null));
                preparedStatement = connection.prepareStatement(selectServerNameStatement);
                preparedStatement.setString(1, instanceName);
                resultSet = preparedStatement.executeQuery();

                if (resultSet.next()) {
                    serverName = resultSet.getString(1);
                    if (_logger.isLoggable(FINE)) {
                        _logger.fine("LogDBHelper found serverName: " + serverName + " for instanceName: " + instanceName);
                    }
                }
            } catch (Exception ex) {
                _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex);
            } finally {
                tryClose(resultSet);
                tryClose(preparedStatement);
                tryClose(connection);
            }
        }
        return serverName;
    }

    private void createTable() {
        if (dataSource != null) {
            if (_logger.isLoggable(FINE)) {
                _logger.fine("LogDBHelper.createTable for instanceName: " + instanceName);
            }

            Connection connection = null;
            Statement statement = null;
            try {
                connection = (Connection) (getNonTxConnectionMethod.invoke(dataSource, null));
                statement = connection.createStatement();
                statement.execute(createTableStatement);
                _logger.fine("=== table created ===");
            } catch (Exception ex) {
                _logger.log(INFO, "jts.exception_in_db_log_resource_create");
                _logger.log(FINE, "jts.exception_in_db_log_table_create_error", ex);
            } finally {
                tryClose(statement);
                tryClose(connection);
            }
        }
    }

    private void tryClose(ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        } catch (Exception ex1) {
            _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex1);
        }
    }

    private void tryClose(Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (Exception ex1) {
            _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex1);
        }
    }

    private void tryClose(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ex1) {
            _logger.log(SEVERE, "jts.exception_in_db_log_resource", ex1);
        }
    }
}
