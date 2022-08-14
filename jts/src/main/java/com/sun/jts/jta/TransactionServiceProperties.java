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

package com.sun.jts.jta;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.transaction.api.ResourceRecoveryManager;
import com.sun.enterprise.transaction.api.TransactionServiceConfig;
import com.sun.enterprise.transaction.spi.ServiceLocator;
import com.sun.jts.CosTransactions.Configuration;
import com.sun.jts.CosTransactions.RecoveryManager;
import com.sun.jts.utils.RecoveryHooks.FailureInducer;
import com.todo.GetORBPort;

/**
 *
 * @author mvatkina
 */
public class TransactionServiceProperties {

    private static Logger _logger = Logger.getLogger(TransactionServiceProperties.class.getName());

    private static final String JTS_XA_SERVER_NAME = "com.sun.jts.xa-servername";
    private static final String J2EE_SERVER_ID_PROP = "com.sun.enterprise.J2EEServerId";
    private static final String JTS_SERVER_ID = "com.sun.jts.persistentServerId";
    private static final String HABITAT = "HABITAT";
    private static final int DEFAULT_SERVER_ID = 100;

    private static Properties properties = null;
    private static volatile boolean orbAvailable = false;
    private static volatile boolean recoveryInitialized = false;

    public static boolean isServer() {
        // ProcessEnvironment processEnv = serviceLocator.getService(ProcessEnvironment.class);
        // processEnv.getProcessType().isServer()
        return true;
    }

    public static synchronized Properties getJTSProperties(ServiceLocator serviceLocator, boolean isORBAvailable) {
        if (orbAvailable == isORBAvailable && properties != null) {
            // We will need to update the properties if ORB availability changed
            return properties;
        }

        Properties jtsProperties = new Properties();
        if (serviceLocator != null) {
            jtsProperties.put(HABITAT, serviceLocator);
            if (isServer()) {
                TransactionServiceConfig txnService = serviceLocator.getService(TransactionServiceConfig.class, "default-instance-name");

                if (txnService != null) {
                    jtsProperties.put(Configuration.HEURISTIC_DIRECTION, txnService.getHeuristicDecision());
                    jtsProperties.put(Configuration.KEYPOINT_COUNT, txnService.getKeypointInterval());

                    String automaticRecovery = txnService.getAutomaticRecovery();
                    boolean isAutomaticRecovery = (isValueSet(automaticRecovery) && "true".equals(automaticRecovery));
                    if (isAutomaticRecovery) {
                        _logger.log(FINE, "Recoverable J2EE Server");
                        jtsProperties.put(Configuration.MANUAL_RECOVERY, "true");
                    }

                    boolean disable_distributed_transaction_logging = false;
                    String dbLoggingResource = null;
                    for (Map.Entry<String, String> prop : txnService.getProperties()) {
                        String name = prop.getKey();
                        String value = prop.getValue();

                        if (name.equals("disable-distributed-transaction-logging")) {
                            if (isValueSet(value) && "true".equals(value)) {
                                disable_distributed_transaction_logging = true;
                            }

                        } else if (name.equals("xaresource-txn-timeout")) {
                            if (isValueSet(value)) {
                                _logger.log(FINE, "XAResource transaction timeout is" + value);
                                TransactionManagerImpl.setXAResourceTimeOut(Integer.parseInt(value));
                            }

                        } else if (name.equals("db-logging-resource")) {
                            dbLoggingResource = value;
                            _logger.log(FINE, "Transaction DB Logging Resource Name" + dbLoggingResource);
                            if (dbLoggingResource != null && (" ".equals(dbLoggingResource) || "".equals(dbLoggingResource))) {
                                dbLoggingResource = "jdbc/TxnDS";
                            }

                        } else if (name.equals("xa-servername")) {
                            if (isValueSet(value)) {
                                jtsProperties.put(JTS_XA_SERVER_NAME, value);
                            }

                        } else if (name.equals("pending-txn-cleanup-interval")) {
                            if (isValueSet(value)) {
                                jtsProperties.put("pending-txn-cleanup-interval", value);
                            }

                        } else if (name.equals(Configuration.COMMIT_ONE_PHASE_DURING_RECOVERY)) {
                            if (isValueSet(value)) {
                                jtsProperties.put(Configuration.COMMIT_ONE_PHASE_DURING_RECOVERY, value);
                            }
                        } else if (name.equals("add-wait-point-during-recovery")) {
                            if (isValueSet(value)) {
                                try {
                                    FailureInducer.setWaitPointRecovery(Integer.parseInt(value));
                                } catch (Exception e) {
                                    _logger.log(WARNING, e.getMessage());
                                }
                            }

                        }
                    }

                    if (dbLoggingResource != null) {
                        disable_distributed_transaction_logging = true;
                        jtsProperties.put(Configuration.DB_LOG_RESOURCE, dbLoggingResource);
                    }

                    /**
                     * JTS_SERVER_ID needs to be unique for each for server instance. This will be used as recovery identifier along with
                     * the hostname for example: if the hostname is 'tulsa' and iiop-listener-port is 3700 recovery identifier will be
                     * tulsa,P3700
                     **/
                    int jtsServerId = DEFAULT_SERVER_ID; // default value

                    if (isORBAvailable) {
                        jtsServerId = GetORBPort.getInitialPort();
                        if (jtsServerId == 0) {
                            // XXX Can this ever happen?
                            jtsServerId = DEFAULT_SERVER_ID; // default value
                        }
                    }
                    jtsProperties.put(JTS_SERVER_ID, String.valueOf(jtsServerId));

                    /*
                     * ServerId is an J2SE persistent server activation API. ServerId is scoped at the ORBD. Since There is no ORBD present
                     * in J2EE the value of ServerId is meaningless - except it must have SOME value if persistent POAs are created.
                     */

                    // For clusters - all servers in the cluster MUST
                    // have the same ServerId so when failover happens
                    // and requests are delivered to a new server, the
                    // ServerId in the request will match the new server.

                    String serverId = String.valueOf(DEFAULT_SERVER_ID);
                    System.setProperty(J2EE_SERVER_ID_PROP, serverId);

                    // ServerContext ctx = serviceLocator.getService(ServerContext.class);
                    String instanceName = "default-instance-name";

                    /**
                     * if the auto recovery is true, always transaction logs will be written irrespective of
                     * disable_distributed_transaction_logging. if the auto recovery is false, then disable_distributed_transaction_logging
                     * will be used to write transaction logs are not.If disable_distributed_transaction_logging is set to false(by default
                     * false) logs will be written, set to true logs won't be written.
                     **/
                    if (!isAutomaticRecovery && disable_distributed_transaction_logging) {
                        Configuration.disableFileLogging();
                    } else {

                        // if (dbLoggingResource == null) {

//                        Domain domain = serviceLocator.getService(Domain.class);
//                        Server server = domain.getServerNamed(instanceName);
//
//                        // Check if the server system property is set
//                        String logdir = getTXLogDir(server);
//
//                        // if not, check if the cluster system property is set
//                        if (logdir == null) {
//                            Cluster cluster = server.getCluster();
//                            if (cluster != null) {
//                                logdir = getTXLogDir(cluster);
//                            }
//                        }

                        // TODO
                        String logdir = null;

                        // No system properties are set - get tx log dir from transaction service
                        if (logdir == null) {
                            logdir = txnService.getTxLogDir();
                        }

                        if (logdir == null) {
                            // logdir = domain.getLogRoot();
                            if (logdir == null) {
                                // logdir = FileUtil.getAbsolutePath(".." + File.separator + "logs");
                                logdir = ".." + File.separator + "logs";
                            }
                        } else if (!(new File(logdir)).isAbsolute()) {
                            if (_logger.isLoggable(FINE)) {
                                _logger.log(FINE, "Relative pathname specified for transaction log directory : " + logdir);
                            }
                            // String logroot = domain.getLogRoot();
                            String logroot = null;
                            if (logroot != null) {
                                logdir = logroot + File.separator + logdir;
                            } else {
                                // logdir = FileUtil.getAbsolutePath(".." + File.separator + "logs"
                                // + File.separator + logdir);
                                logdir = ".." + File.separator + "logs" + File.separator + logdir;
                            }
                        }
                        logdir += File.separator + instanceName + File.separator + "tx";

                        if (_logger.isLoggable(FINE)) {
                            _logger.log(FINE, "JTS log directory: " + logdir);
                            _logger.log(FINE, "JTS Server id " + jtsServerId);
                        }

                        jtsProperties.put(Configuration.LOG_DIRECTORY, logdir);
                    }
                    jtsProperties.put(Configuration.COMMIT_RETRY, txnService.getRetryTimeoutInSeconds());
                    jtsProperties.put(Configuration.INSTANCE_NAME, instanceName);

                }
            }
        }

        properties = jtsProperties;
        orbAvailable = isORBAvailable;

        return properties;
    }

    public static void initRecovery(boolean force) {
        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "initRecovery:recoveryInitialized: " + recoveryInitialized);
        }

        if (recoveryInitialized) {
            // Only start initial recovery if it wasn't started before
            return;
        }

        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "initRecovery:properties: " + properties);
        }
        if (properties == null) {
            if (force) {
                _logger.log(WARNING, "", new IllegalStateException());
            }
            return;
        }

        // Start if force is true or automatic-recovery is set
        String value = properties.getProperty(Configuration.MANUAL_RECOVERY);
        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "initRecovery:Configuration.MANUAL_RECOVERY: " + value);
        }
        if (force || (isValueSet(value) && "true".equals(value))) {
            recoveryInitialized = true;

            ServiceLocator serviceLocator = (ServiceLocator) properties.get(HABITAT);
            if (serviceLocator != null) {
                if (isServer()) {
                    // Start ResourceManager if it hadn't started yet
                    // TODO
                    // serviceLocator.getAllServices(BuilderHelper.createNameFilter("ResourceManager"));
                    value = properties.getProperty("pending-txn-cleanup-interval");
                    int interval = -1;
                    if (isValueSet(value)) {
                        interval = Integer.parseInt(value);
                    }
                    new RecoveryHelperThread(serviceLocator, interval).start();
                }
                // Release all locks
                RecoveryManager.startResyncThread();
                if (_logger.isLoggable(FINE))
                    _logger.log(FINE, "[JTS] Started ResyncThread");
            }
        }
    }

    private static boolean isValueSet(String value) {
        return (value != null && !value.equals("") && !value.equals(" "));
    }

//    private static String getTXLogDir(SystemPropertyBag bag) {
//        for (SystemProperty prop : bag.getSystemProperty()) {
//            String name = prop.getName();
//            if (name.equals("TX-LOG-DIR")) {
//                return prop.getValue();
//            }
//        }
//
//        return null;
//    }

    private static class RecoveryHelperThread extends Thread {
        private int interval;
        private ServiceLocator serviceLocator;

        RecoveryHelperThread(ServiceLocator serviceLocator, int interval) {
            setName("Recovery Helper Thread");
            setDaemon(true);
            this.serviceLocator = serviceLocator;
            this.interval = interval;
        }

        @Override
        public void run() {
            ResourceRecoveryManager recoveryManager = serviceLocator.getService(ResourceRecoveryManager.class);
            if (interval <= 0) {
                // Only start the recovery thread if the interval value is set, and set to a positive value
                return;
            }

            if (_logger.isLoggable(Level.INFO)) {
                _logger.log(Level.INFO, "Asynchronous thread for incomplete " + "tx is enabled with interval " + interval);
            }
            int prevSize = 0;
            try {
                while (true) {
                    Thread.sleep(interval * 1000L);
                    if (!RecoveryManager.isIncompleteTxRecoveryRequired()) {
                        if (_logger.isLoggable(FINE))
                            _logger.log(FINE,
                                    "Incomplete transaction recovery is " + "not requeired,  waiting for the next interval");
                        continue;
                    }
                    if (RecoveryManager.sizeOfInCompleteTx() <= prevSize) {
                        if (_logger.isLoggable(FINE))
                            _logger.log(FINE,
                                    "Incomplete transaction recovery is " + "not required,  waiting for the next interval SIZE");
                        continue;
                    }
                    prevSize = RecoveryManager.sizeOfInCompleteTx();
                    recoveryManager.recoverIncompleteTx(false, null);
                }
            } catch (Exception ex) {
                if (_logger.isLoggable(FINE))
                    _logger.log(FINE, " Exception occurred in recoverInCompleteTx ");
            }
        }
    }
}
