/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 1995-1997 IBM Corp. All rights reserved.
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

//----------------------------------------------------------------------------
//
// Module:      Configuration.java
//
// Description: JTS configuration management.
//
// Product:     ee.omnifish.transact.jts.CosTransactions
//
// Author: Simon Holdsworth
//
// Date:        March, 1997
//----------------------------------------------------------------------------

package ee.omnifish.transact.jts.CosTransactions;

import static java.util.logging.Level.FINE;

// Import required classes.
import java.io.File;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import org.omg.CORBA.ORB;
import org.omg.CosTransactions.TransactionFactory;
import org.omg.PortableServer.POA;

import ee.omnifish.transact.api.TransactionConstants;
import ee.omnifish.transact.jts.utils.LogFormatter;

/**
 * Provides interaction with the execution environment.
 *
 * @version 0.01
 *
 * @author Simon Holdsworth, IBM Corporation
 *
 */
// CHANGE HISTORY
//
// Version By     Change Description
//   0.01  SAJH   Initial implementation.
//------------------------------------------------------------------------------

public class Configuration {

    /*
     * Logger to log transaction messages
     */
    static Logger _logger = Logger.getLogger(Configuration.class.getName());

    private static String serverName;
    private static byte[] serverNameByteArray;
    private static org.omg.CORBA.ORB orb;
    private static Properties prop;
    private static TransactionFactory factory;
    private static boolean localFactory;
    private static boolean recoverable;
    private static ProxyChecker checker;
    private static LogFile logFile;
    private static Hashtable poas = new Hashtable(); //  Portable Object Adapters
    private static String dbLogResource;
    private static boolean disableFileLogging;

    // for delegated recovery support
    private static Hashtable logPathToServernametable = new Hashtable();
    private static Hashtable logPathToFiletable = new Hashtable();

    private static int retries = -1;
    public static final String COMMIT_ONE_PHASE_DURING_RECOVERY = "commit-one-phase-during-recovery";
    public static final int LAO_PREPARE_OK = TransactionConstants.LAO_PREPARE_OK;
    public final static long COMMIT_RETRY_WAIT = 60000;
    private static boolean isAppClient = true;

    /**
     * The traceOn would enable/disable JTS wide tracing; (Related class: ee.omnifish.transact.jts.trace.TraceUtil) -
     * kannan.srinivasan@Sun.COM 27Nov2001
     */
    private static boolean traceOn;

    /**
     * The property key used to specify the directory to which trace files and the error log should be written.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.traceDirectory</b></em>.
     * <p>
     * The default value used for this property is the current directory.
     */
    public final static String TRACE_DIRECTORY = "ee.omnifish.transact.jts.traceDirectory";

    /**
     * The property key used to specify the directory to which transaction log files should be written.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.logDirectory</b></em>.
     * <p>
     * The default value used for this property is the "jts" subdirectory from the current directory, if that exists,
     * otherwise the current directory.
     */
    public final static String LOG_DIRECTORY = "ee.omnifish.transact.jts.logDirectory";

    /**
     * The property key used to specify the resource which will be used to write transaction logs.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.logResource</b></em>.
     * <p>
     */
    public final static String DB_LOG_RESOURCE = "ee.omnifish.transact.jts.logResource";

    /**
     * Whether to write warnings and errors to jts.log file if this property has any value, it is active, otherwise it is
     * inactive
     *
     */
    public final static String ERR_LOGGING = "ee.omnifish.transact.jts.errorLogging";

    /**
     * This property indicates that XA Resources would be passed in via the TM.recover() method, and that the recovery
     * thread would have to wait until the resources are passed in. If not set, the recovery thread would not wait for the
     * XA Resources to be passed in.
     */
    public final static String MANUAL_RECOVERY = "ee.omnifish.transact.jts.ManualRecovery";

    /**
     * The property key used to specify the number of times the JTS should retry a commit or resync operation before giving
     * up.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.commitRetry</b></em>.
     * <p>
     * If this property has no value, retries continue indefinitely. A value of zero indicates that no retries should be
     * made.
     */
    public final static String COMMIT_RETRY = "ee.omnifish.transact.jts.commitRetry";

    /**
     * The property key used to specify whether the JTS should assume a transaction is to be committed or rolled back if an
     * outcome cannot be obtained during recovery. It should also be used by Resource objects if they cannot obtain an
     * outcome during recovery and cannot make a decision.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.heuristicDirection</b></em>.
     * <p>
     * The default is to assume that the transaction should be rolled back. If the value is '1', the transaction should be
     * committed.
     */
    public final static String HEURISTIC_DIRECTION = "ee.omnifish.transact.jts.heuristicDirection";

    /**
     * The property key used to specify the number of transactions between keypoint operations on the log. Keypoint
     * operations reduce the size of the transaction log files. A larger value for this property (for example, 1000) will
     * result in larger transaction log files, but less keypoint operations, and hence better performance. a smaller value
     * (e.g. 20) results in smaller log files but slightly reduced performance due to the greater frequency of keypoint
     * operations.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.keypointCount</b></em>.
     * <p>
     * The default value for this property is 100. If the value is specified as zero, then no keypoints are taken.
     */
    public final static String KEYPOINT_COUNT = "ee.omnifish.transact.jts.keypointCount";

    // Property to specify the instance name
    public final static String INSTANCE_NAME = "ee.omnifish.transact.jts.instancename";

    /**
     * The property is used to specify the time interval in seconds for which the timeout manager would scan for timedout
     * transactions. A higher value would mean better performance, but at the cost of closeness to which coordinator timeout
     * is effected.
     * <p>
     * The value is <em><b>ee.omnifish.transact.jts.timeoutInterval"</b></em>
     * <p>
     * This needs to be a positive integer value greater than 10. If the value is less than 10, illegal or unspecified a
     * default value of 10 seconds is assumed.
     */
    public final static String TIMEOUT_INTERVAL = "ee.omnifish.transact.jts.timeoutInterval";

    /**
     * The default subdirectory in which log and repository files are stored.
     */
    public final static String JTS_SUBDIRECTORY = "jts";

    /**
     * getDirectory return value which indicates that the required directory was specified and is OK.
     */
    public final static int DIRECTORY_OK = 0;

    /**
     * getDirectory return value which indicates that the required directory was either not specified or was specified and
     * invalid, and that the default subdirectory exists. In this case the default subdirectory should be used.
     */
    public final static int DEFAULT_USED = 1;

    /**
     * getDirectory return value which indicates that the required directory was either not specified or was specified and
     * invalid, and that the default subdirectory does not exist. In this case the current directory should be used.
     */
    public final static int DEFAULT_INVALID = 2;

    /**
     * The approximate concurrent transactions expected. This is used to set the capacity of Vectors etc.
     */
    public final static int EXPECTED_CONCURRENT_TRANSACTIONS = 10000;

    /**
     * The approximate concurrent transactions expected. This is used to set the capacity of Vectors etc.
     */
    public final static int EXPECTED_CONCURRENT_THREADS = 100;

    /**
     * Returns a valid directory for a particular purpose. If the required directory is not valid, then a default
     * subdirectory of the current directory is tried. If that is not valid either, then the current directory is used.
     *
     * @param envDir The environment variable containing the directory.
     * @param defaultSubdirectory The default subdirectory to use.
     * @param result A single-element array which will hold a value indicating whether the requested directory, default
     * subdirectory, or current directory had to be used.
     *
     * @return The directory name.
     *
     */
    public static String getDirectory(String envDir, String defaultSubdirectory, int[/* 1 */] result) {
        // Get the environment variable value.

        String envValue = null;
        if (prop != null) {
            envValue = prop.getProperty(envDir);
        }

        // If the environment variable is not set, or does not refer to a valid
        // directory, then try to use a default.

        result[0] = DIRECTORY_OK;

        if (envValue == null || envValue.length() == 0 || (new File(envValue).exists() && !new File(envValue).isDirectory())) {
            result[0] = DEFAULT_USED;

            // If the default subdirectory is not valid, then use the current directory.

            envValue = "." + File.separator + defaultSubdirectory/* #Frozen */;
            if (new File(envValue).exists() && !new File(envValue).isDirectory()) {
                result[0] = DEFAULT_INVALID;
            }
        }

        if (_logger.isLoggable(FINE)) {
            String dirType = "";
            switch (result[0]) {
                case DEFAULT_INVALID:
                    dirType = "used default, but is invalid";
                    break;
                case DEFAULT_USED:
                    dirType = "used default";
                    break;
                case DIRECTORY_OK:
                    dirType = "provided in configuration";
                    break;
                default:
                    dirType = "invalid type";
                    break;
                }
            _logger.logp(FINE, "Configuration", "getDirectory()", "Using directory = " + envValue + " : " + dirType);
        }

        return envValue;
    }

    /**
     * Sets the name of the server.
     *
     * @param name The server name. Non-recoverable servers have null.
     */
    public static final void setServerName(String name, boolean recoverableServer) {

        // Store the server name.

        serverName = name;
        serverNameByteArray = (name == null) ? null : serverName.getBytes();
        recoverable = recoverableServer;
        if (recoverable) {
            RecoveryManager.createRecoveryFile(serverName);
        }

        if (_logger.isLoggable(FINE)) {
            _logger.logp(FINE, "Configuration", "setServerName()",
                    " serverName = " + serverName + "; isRecoverable = " + recoverable);
        }
    }

    /**
     * Returns the name of the server.
     * <p>
     * Non-recoverable servers may not have a name, in which case the method returns null.
     *
     * @return The server name.
     */
    public static final String getServerName() {
        return serverName;
    }

    /**
     * Sets the name of the server for the given log path. Added for delegated recovery support.
     *
     * @param logPath Location, where the logs are stored.
     * @param name The server name.
     */
    public static final void setServerName(String logPath, String name) {
        logPathToServernametable.put(logPath, name);
    }

    /**
     * Returns the name of the server for the given log path. Added for delegated recovery support.
     *
     * @param logPath location of the log files.
     * @return The server name.
     */
    public static final String getServerName(String logPath) {
        return (String) logPathToServernametable.get(logPath);
    }

    /**
     * Returns a byte array with the name of the server.
     * <p>
     * Non-recoverable servers may not have a name, in which case the method returns null.
     *
     * @return The server name (byte array).
     */
    public static final byte[] getServerNameByteArray() {
        return serverNameByteArray;
    }

    /**
     * Sets the Properties object to be used for this JTS instance.
     *
     * @param newProp The Properties.
     */
    public static final void setProperties(Properties newProp) {
        // Store the Properties object.
        if (prop == null) {
            prop = newProp;
        } else if (newProp != null) {
            prop.putAll(newProp);
        }

        if (_logger.isLoggable(FINE)) {
            String propertiesList = LogFormatter.convertPropsToString(prop);
            _logger.logp(FINE, "Configuration", "setProperties()", " Properties set are :" + propertiesList);
        }

        if (prop != null) {
            dbLogResource = prop.getProperty(DB_LOG_RESOURCE);
            String retryLimit = prop.getProperty(COMMIT_RETRY);
            int retriesInMinutes;
            if (retryLimit != null) {
                retriesInMinutes = Integer.parseInt(retryLimit, 10);
                if ((retriesInMinutes % (COMMIT_RETRY_WAIT / 1000)) == 0) {
                    retries = (int) (retriesInMinutes / (COMMIT_RETRY_WAIT / 1000));
                } else {
                    retries = ((int) ((retriesInMinutes / (COMMIT_RETRY_WAIT / 1000)))) + 1;
                }
            }
        }

    }

    /**
     * Returns the value of the given variable.
     *
     * @param envValue The environment variable required.
     * @return The value.
     */
    public static final String getPropertyValue(String envValue) {
        // Get the environment variable value.
        String result = null;
        if (prop != null) {
            result = prop.getProperty(envValue);
            if (_logger.isLoggable(FINE)) {
                _logger.log(FINE, "Property :" + envValue + " has the value : " + result);

            }
        }

        return result;
    }

    /**
     * Sets the identity of the ORB.
     *
     * @param newORB The ORB.
     */
    public static final void setORB(ORB newORB) {
        orb = newORB;
    }

    /**
     * Returns the identity of the ORB.
     *
     * @return The ORB.
     */
    public static final ORB getORB() {
        return orb;
    }

    /**
     * Sets the identity of the TransactionFactory and indicates if it is local or remote.
     *
     * @param newFactory The TransactionFactory.
     * @param localTxFactory Indicates if the factory is local or remote.
     */
    public static final void setFactory(TransactionFactory newFactory, boolean localTxFactory) {
        // Store the factory identity and if it is local or not.
        factory = newFactory;
        localFactory = localTxFactory;
    }

    /**
     * Returns the identity of the TransactionFactory.
     *
     * @return The TransactionFactory.
     */
    public static final TransactionFactory getFactory() {
        return factory;
    }

    /**
     * Determines whether we hava a local factory or a remote factory.
     *
     * @return Indicates whether we have a local factory.
     */
    public static final boolean isLocalFactory() {
        // This is a local factory if localFactory is TRUE
        return localFactory;
    }

    /**
     * Determines whether the JTS instance is recoverable.
     *
     * @return Indicates whether the JTS is recoverable.
     */
    public static final boolean isRecoverable() {
        // This JTS is recoverable if recoverable is set to TRUE.
        return recoverable;
    }

    /**
     * Sets the identity of the ProxyChecker.
     *
     * @param newChecker The new ProxyChecker.
     */
    public static final void setProxyChecker(ProxyChecker newChecker) {
        checker = newChecker;
    }

    /**
     * Returns the identity of the ProxyChecker.
     *
     * @return The ProxyChecker.
     */
    public static final ProxyChecker getProxyChecker() {
        return checker;
    }

    /**
     * Sets the identity of the log file for the process.
     *
     * @param newLogFile The new LogFile object.
     */
    public static final void setLogFile(LogFile newLogFile) {
        logFile = newLogFile;
    }

    /**
     * Returns the identity of the LogFile for the process.
     *
     * @return The LogFile.
     */
    public static final LogFile getLogFile() {
        return logFile;
    }

    /**
     * Sets the log file for the given log path. For delegated recovery support.
     *
     * @param logPath The new LogFile object.
     * @param newLogFile The new LogFile object.
     */
    public static final void setLogFile(String logPath, LogFile newLogFile) {
        logPathToFiletable.put(logPath, newLogFile);
    }

    /**
     * Returns the LogFile for the given log path. For delegated recovery support.
     *
     * @param logPath log location.
     * @return The LogFile.
     */
    public static final LogFile getLogFile(String logPath) {
        if (logPath == null) {
            return null;
        }

        return (LogFile) logPathToFiletable.get(logPath);
    }

    /**
     * Sets the identity of the POA to be used for the given types of object.
     *
     * @param type The type of objects to use the POA.
     * @param poa The POA object.
     */
    public static final void setPOA(String type, POA poa) {
        // Store the mapping.
        poas.put(type, poa);
    }

    /**
     * Returns the identity of the POA to be used for the given type of objects.
     *
     * @param type The type of objects
     * @return The POA.
     */
    public static final POA getPOA(String type) {
        return (POA) poas.get(type);
    }

    public static final boolean isTraceEnabled() {
        return traceOn;
    }

    public static final void enableTrace() {
        traceOn = true;
    }

    public static final void disableTrace() {
        traceOn = false;
    }

    public static void setKeypointTrigger(int keypoint) {
        CoordinatorLogPool.getCoordinatorLog();
        CoordinatorLog.setKeypointTrigger(keypoint);
    }

    public static void setCommitRetryVar(String commitRetryString) {
        // RegisteredResources.setCommitRetryVar(commitRetryString);
        if (commitRetryString != null) {
            int retriesInMinutes = Integer.parseInt(commitRetryString, 10);
            if ((retriesInMinutes % (COMMIT_RETRY_WAIT / 1000)) == 0) {
                retries = (int) (retriesInMinutes / (COMMIT_RETRY_WAIT / 1000));
            } else {
                retries = ((int) (retriesInMinutes / (COMMIT_RETRY_WAIT / 1000))) + 1;
            }
        }
    }

    public static int getRetries() {
        return retries;
    }

    public static void setAsAppClientConatiner(boolean value) {
        isAppClient = value;
    }

    public static boolean isAppClientContainer() {
        return isAppClient;
    }

    public static boolean isDBLoggingEnabled() {
        return dbLogResource != null;
    }

    public static void disableFileLogging() {
        disableFileLogging = true;
    }

    public static boolean isFileLoggingDisabled() {
        return disableFileLogging;
    }

}
