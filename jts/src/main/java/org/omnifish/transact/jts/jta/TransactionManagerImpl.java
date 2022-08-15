/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package org.omnifish.transact.jts.jta;

import static jakarta.transaction.Status.STATUS_ACTIVE;
import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_COMMITTING;
import static jakarta.transaction.Status.STATUS_MARKED_ROLLBACK;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_PREPARED;
import static jakarta.transaction.Status.STATUS_PREPARING;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static jakarta.transaction.Status.STATUS_ROLLING_BACK;
import static jakarta.transaction.Status.STATUS_UNKNOWN;
import static java.util.logging.Level.SEVERE;
import static org.omg.CosTransactions.Status.StatusActive;
import static org.omg.CosTransactions.Status.StatusCommitted;
import static org.omg.CosTransactions.Status.StatusCommitting;
import static org.omg.CosTransactions.Status.StatusMarkedRollback;
import static org.omg.CosTransactions.Status.StatusNoTransaction;
import static org.omg.CosTransactions.Status.StatusPrepared;
import static org.omg.CosTransactions.Status.StatusPreparing;
import static org.omg.CosTransactions.Status.StatusRolledBack;
import static org.omg.CosTransactions.Status.StatusRollingBack;
import static org.omg.CosTransactions.Status.StatusUnknown;

import java.io.File;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import javax.transaction.xa.Xid;

import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.INVALID_TRANSACTION;
import org.omg.CORBA.NO_PERMISSION;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TRANSACTION_ROLLEDBACK;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.Current;
import org.omg.CosTransactions.HeuristicHazard;
import org.omg.CosTransactions.HeuristicMixed;
import org.omg.CosTransactions.InvalidControl;
import org.omg.CosTransactions.NoTransaction;
import org.omg.CosTransactions.Status;
import org.omg.CosTransactions.SubtransactionsUnavailable;
import org.omg.CosTransactions.Unavailable;
import org.omnifish.transact.api.api.Globals;
import org.omnifish.transact.jts.CosTransactions.Configuration;
import org.omnifish.transact.jts.CosTransactions.ControlImpl;
import org.omnifish.transact.jts.CosTransactions.CurrentTransaction;
import org.omnifish.transact.jts.CosTransactions.DefaultTransactionService;
import org.omnifish.transact.jts.CosTransactions.GlobalTID;
import org.omnifish.transact.jts.CosTransactions.MinorCode;
import org.omnifish.transact.jts.CosTransactions.RecoveryManager;
import org.omnifish.transact.jts.CosTransactions.XATerminatorImpl;
import org.omnifish.transact.jts.codegen.otsidl.JControlHelper;
import org.omnifish.transact.jts.utils.LogFormatter;

import jakarta.resource.spi.work.WorkCompletedException;
import jakarta.resource.spi.work.WorkException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

/**
 * An implementation of jakarta.transaction.TransactionManager using JTA.
 *
 * This is a singleton object
 *
 * @author Tony Ng
 */
public class TransactionManagerImpl implements TransactionManager {

    /**
     * Logger to log transaction messages
     */
    static Logger _logger = Logger.getLogger(TransactionManagerImpl.class.getName());

    /**
     * the singleton object
     */
    static private TransactionManagerImpl tm;

    /**
     * store the current psuedo object
     */
    private Current current;

    static private int[] directLookup;
    static final int maxStatus;

    /**
     * store XAResource Timeout
     */
    static private int xaTimeOut = 0;

    static private Status CosTransactionStatus[] = {
            StatusActive,
            StatusMarkedRollback,
            StatusPrepared,
            StatusCommitted,
            StatusRolledBack,
            StatusUnknown,
            StatusNoTransaction,
            StatusPreparing,
            StatusCommitting,
            StatusRollingBack };

    static private int JTAStatus[] = {
            STATUS_ACTIVE,
            STATUS_MARKED_ROLLBACK,
            STATUS_PREPARED,
            STATUS_COMMITTED,
            STATUS_ROLLEDBACK,
            STATUS_UNKNOWN,
            STATUS_NO_TRANSACTION,
            STATUS_PREPARING,
            STATUS_COMMITTING, STATUS_ROLLING_BACK };

    static {
        int calcMaxStatus = 0;
        for (Status element : CosTransactionStatus) {
            calcMaxStatus = Math.max(calcMaxStatus, element.value());
        }
        maxStatus = calcMaxStatus;
        directLookup = new int[maxStatus + 1];
        for (int i = 0; i < directLookup.length; i++) {
            // Initialize so that any unused slots point to 'unkown'.
            directLookup[i] = STATUS_UNKNOWN;
        }

        for (int i = 0; i < CosTransactionStatus.length; i++) {
            int statusVal = CosTransactionStatus[i].value();
            if (statusVal < 0) {
                _logger.log(SEVERE, "A negative CosTransaction Status value was detected.");
            } else {
                directLookup[statusVal] = JTAStatus[i];
            }
        }

    }

    /**
     * get the singleton TransactionManagerImpl
     */
    static synchronized public TransactionManagerImpl getTransactionManagerImpl() {
        if (tm == null) {
            tm = new TransactionManagerImpl();
        }

        return tm;
    }

    /**
     * Create a transaction manager instance
     */
    private TransactionManagerImpl() {
        try {
            ORB orb = Configuration.getORB();
            if (orb != null) {
                current = org.omg.CosTransactions.CurrentHelper.narrow(orb.resolve_initial_references("TransactionCurrent"/* #Frozen */));
            } else {
                DefaultTransactionService dts = new DefaultTransactionService();
                Properties p = TransactionServiceProperties.getJTSProperties(Globals.getDefaultServiceLocator(), false);
                if (!Configuration.isFileLoggingDisabled()) {
                    String logdir = p.getProperty(Configuration.LOG_DIRECTORY);
                    _logger.fine("======= logdir ======= " + logdir);
                    if (logdir != null) {
                        (new File(logdir)).mkdirs();
                    }
                }

                dts.identify_ORB(null, null, p);
                current = dts.get_current();
            }

            // This will release locks in RecoveryManager which were created
            // by RecoveryManager.initialize() call in the TransactionFactoryImpl constructor
            // if startup recovery didn't happen yet.
            TransactionServiceProperties.initRecovery(true);

        } catch (InvalidName inex) {
            _logger.log(SEVERE, "jts.unexpected_error_in_create_transaction_manager", inex);
        } catch (Exception ex) {
            _logger.log(SEVERE, "jts.unexpected_error_in_create_transaction_manager", ex);
        }
    }

    /**
     * extends props with the JTS-related properties based on the specified parameters. The properties will be used as part
     * of ORB.init() call.
     *
     * @param prop the properties that will be extended
     * @param logDir directory for the log, current directory if null
     * @param trace enable JTS tracing
     * @param traceDir directory for tracing, current directory if null
     *
     */
    static public void initJTSProperties(Properties props, String logDir, boolean trace, String traceDir) {
        if (traceDir == null) {
            traceDir = "."/* #Frozen */;
        }
        if (logDir == null) {
            logDir = "."/* #Frozen */;
        }

        props.put("com.sun.corba.se.CosTransactions.ORBJTSClass"/* #Frozen */,
                "org.omnifish.transact.jts.CosTransactions.DefaultTransactionService"/* #Frozen */);
        props.put("org.omnifish.transact.jts.traceDirectory"/* #Frozen */, traceDir);
        props.put("org.omnifish.transact.jts.logDirectory"/* #Frozen */, logDir);
        if (trace) {
            props.put("org.omnifish.transact.jts.trace"/* #Frozen */, "true"/* #Frozen */);
        }
    }

    /**
     * given a CosTransactions Status, return the equivalent JTA Status
     */
    static public int mapStatus(Status status) {
        int statusVal = status.value();
        if (statusVal < 0 || statusVal > maxStatus) {
            return jakarta.transaction.Status.STATUS_UNKNOWN;
        }

        return directLookup[statusVal];
    }

    /**
     * Create a new transaction and associate it with the current thread.
     *
     * @exception NotSupportedException Thrown if the thread is already associated with a transaction.
     */
    @Override
    public void begin() throws NotSupportedException, SystemException {

        try {
            // does not support nested transaction
            if (current.get_control() != null) {
                throw new NotSupportedException();
            }
            current.begin();
        } catch (TRANSACTION_ROLLEDBACK ex) {
            throw new NotSupportedException();
        } catch (SubtransactionsUnavailable ex) {
            throw new SystemException();
        }
    }

    // START IASRI PERFIMPROVEMNT
    /**
     * Create a new transaction with the given timeout and associate it with the current thread.
     *
     * @exception NotSupportedException Thrown if the thread is already associated with a transaction.
     */
    public void begin(int timeout) throws NotSupportedException, SystemException {
        try {
            // does not support nested transaction
            if (current.get_control() != null) {
                throw new NotSupportedException();
            }
            ((org.omnifish.transact.jts.CosTransactions.CurrentImpl) current).begin(timeout);
        } catch (TRANSACTION_ROLLEDBACK ex) {
            throw new NotSupportedException();
        } catch (SubtransactionsUnavailable ex) {
            throw new SystemException();
        }
    }
    // END IASRI PERFIMPROVEMNT

    /**
     * Complete the transaction associated with the current thread. When this method completes, the thread becomes
     * associated with no transaction.
     *
     * @exception RollbackException Thrown to indicate that the transaction has been rolled back rather than committed.
     *
     * @exception HeuristicMixedException Thrown to indicate that a heuristic decision was made and that some relevant
     * updates have been committed while others have been rolled back.
     *
     * @exception HeuristicRollbackException Thrown to indicate that a heuristic decision was made and that all relevant
     * updates have been rolled back.
     *
     * @exception SecurityException Thrown to indicate that the thread is not allowed to commit the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is not associated with a transaction.
     */
    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException,
            IllegalStateException, SystemException {

        try {
            current.commit(true);
        } catch (TRANSACTION_ROLLEDBACK ex) {
            RollbackException rbe = new RollbackException();
            Throwable cause = ex.getCause();
            if (cause != null) {
                rbe.initCause(cause);
            }
            throw rbe;
        } catch (NoTransaction ex) {
            throw new IllegalStateException();
        } catch (NO_PERMISSION ex) {
            throw new SecurityException();
        } catch (HeuristicMixed ex) {
            throw new HeuristicMixedException();
        } catch (HeuristicHazard ex) {
            throw new HeuristicRollbackException();
        } catch (Exception ex) {
            // ex.printStackTrace();
            throw new SystemException(ex.toString());
        }
        /***
         * Transaction tran = getTransaction(); if (tran == null) throw new IllegalStateException(); tran.commit();
         ***/
    }

    /**
     * Roll back the transaction associated with the current thread. When this method completes, the thread becomes
     * associated with no transaction.
     *
     * @exception SecurityException Thrown to indicate that the thread is not allowed to roll back the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is not associated with a transaction.
     */
    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {

        try {
            current.rollback();
        } catch (NoTransaction ex) {
            throw new IllegalStateException();
        } catch (NO_PERMISSION ex) {
            throw new SecurityException();
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }

        /***
         * Transaction tran = getTransaction(); if (tran == null) throw new IllegalStateException(); tran.rollback();
         ***/
    }

    /**
     * Modify the transaction associated with the current thread such that the only possible outcome of the transaction is
     * to roll back the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is not associated with a transaction.
     */
    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {

        try {
            current.rollback_only();
        } catch (NoTransaction ex) {
            throw new IllegalStateException();
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * Obtain the status of the transaction associated with the current thread.
     *
     * @return The transaction status. If no transaction is associated with the current thread, this method returns the
     * Status.NoTransaction value.
     */
    @Override
    public int getStatus() throws SystemException {
        try {
            Status status = current.get_status();
            return mapStatus(status);
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * Modify the timeout value that is associated with transactions started by subsequent invocations of the begin method.
     *
     * <p>
     * If an application has not called this method, the transaction service uses some default value for the transaction
     * timeout.
     *
     * @param seconds The value of the timeout in seconds. If the value is zero, the transaction service restores the
     * default value. If the value is negative a SystemException is thrown.
     *
     * @exception SystemException Thrown if the transaction manager encounters an unexpected error condition.
     *
     */
    @Override
    public synchronized void setTransactionTimeout(int seconds) throws SystemException {

        try {
            if (seconds < 0) {
                String msg = LogFormatter.getLocalizedMessage(_logger, "jts.invalid_timeout");
                throw new SystemException(msg);
            }
            current.set_timeout(seconds);
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * Get the transaction object that represents the transaction context of the calling thread
     */
    @Override
    public Transaction getTransaction() throws SystemException {

        try {
            Control control = current.get_control();
            if (control == null) {
                return null;
            } else {
                return createTransactionImpl(control);
            }
        } catch (Unavailable uex) {
            throw new SystemException(uex.toString());
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * Resume the transaction context association of the calling thread with the transaction represented by the supplied
     * Transaction object. When this method returns, the calling thread is associated with the transaction context
     * specified.
     */
    @Override
    public void resume(Transaction suspended) throws InvalidTransactionException, IllegalStateException, SystemException {
        // thread is already associated with a transaction?
        if (getTransaction() != null) {
            throw new IllegalStateException();
        }
        // check for invalid Transaction object
        if ((suspended == null) || !(suspended instanceof TransactionImpl)) {
            throw new InvalidTransactionException();
        }
        Control control = ((TransactionImpl) suspended).getControl();
        try {
            current.resume(control);
        } catch (InvalidControl ex) {
            // _logger.log(Level.FINE,"Invalid Control Exception in resume",ex);
            throw new InvalidTransactionException();
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * Suspend the transaction currently associated with the calling thread and return a Transaction object that represents
     * the transaction context being suspended. If the calling thread is not associated with a transaction, the method
     * returns a null object reference. When this method returns, the calling thread is associated with no transaction.
     */
    @Override
    public Transaction suspend() throws SystemException {
        try {
            Control control = current.suspend();
            if (control == null) {
                return null;
            }
            return createTransactionImpl(control);
        } catch (Unavailable uex) {
            throw new SystemException(uex.toString());
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }
    }

    /**
     * TransactionState getOrCreateTransactionState(GlobalTID gtid, Transaction tran) throws SystemException {
     *
     * synchronized (transactionStates) { TransactionState result = (TransactionState) transactionStates.get(gtid); if
     * (result == null) { result = new TransactionState(gtid); transactionStates.put(gtid, result); try { // remove
     * Transaction State on transaction completion Synchronization sync = new SynchronizationListener(gtid, result);
     * tran.registerSynchronization(sync); } catch (Exception ex) { _logger.log(Level.WARNING,
     * "jts.unexpected_error_in_get_or_create_transaction_state",ex); throw new SystemException(); } } return result; } }
     *
     * TransactionState getTransactionState(GlobalTID gtid, Transaction tran) throws SystemException {
     *
     * synchronized (transactionStates) { return (TransactionState) transactionStates.get(gtid); } }
     *
     **/

    private Transaction createTransactionImpl(Control control) throws Unavailable, SystemException {
        GlobalTID gtid = null;
        if (Configuration.isLocalFactory()) {
            gtid = ((ControlImpl) control).getGlobalTID();
        } else {
            ControlImpl cntrlImpl = ControlImpl.servant(JControlHelper.narrow(control));
            gtid = cntrlImpl.getGlobalTID();
        }

        // return new TransactionImpl(this, control, gtid);
        return new TransactionImpl(control, gtid);
    }

    /**
     * The application server passes in the list of XAResource objects to be recovered.
     *
     * @param xaResourceList list of XAResource objects.
     */
    public static void recover(Enumeration xaResourceList) {
        RecoveryManager.recoverXAResources(xaResourceList);
    }

    /**
     * Recreate a transaction based on the Xid. This call causes the calling thread to be associated with the specified
     * transaction.
     *
     * @param xid the Xid object representing a transaction.
     * @param timeout positive, non-zero value for transaction timeout.
     */
    public static void recreate(Xid xid, long timeout) throws WorkException {

        // check if xid is valid
        if (xid == null || xid.getFormatId() == 0 || xid.getBranchQualifier() == null || xid.getGlobalTransactionId() == null) {
            WorkException workExc = new WorkCompletedException("Invalid Xid");
            workExc.setErrorCode(WorkException.TX_RECREATE_FAILED);
            throw workExc;
        }

        // has TransactionService been initialized?
        if (!DefaultTransactionService.isActive()) {
            WorkException workExc = new WorkCompletedException("Transaction Manager unavailable");
            workExc.setErrorCode(WorkException.TX_RECREATE_FAILED);
            throw workExc;
        }

        // recreate the transaction
        GlobalTID tid = new GlobalTID(xid);
        try {
            CurrentTransaction.recreate(tid, (int) ((timeout <= 0) ? 0 : timeout));
        } catch (Throwable exc) {
            String errorCode = WorkException.TX_RECREATE_FAILED;
            if (exc instanceof INVALID_TRANSACTION && (((INVALID_TRANSACTION) exc).minor == MinorCode.TX_CONCURRENT_WORK_DISALLOWED)) {
                errorCode = WorkException.TX_CONCURRENT_WORK_DISALLOWED;
            }
            WorkException workExc = new WorkCompletedException(exc);
            workExc.setErrorCode(errorCode);
            throw workExc;
        }
    }

    /**
     * Release a transaction. This call causes the calling thread to be dissociated from the specified transaction.
     *
     * @param xid the Xid object representing a transaction.
     */
    public static void release(Xid xid) throws WorkException {

        GlobalTID tid = new GlobalTID(xid);
        try {
            CurrentTransaction.release(tid);
        } catch (Throwable exc) {
            String errorCode = WorkException.UNDEFINED;
            if (exc instanceof INTERNAL) {
                errorCode = WorkException.INTERNAL;
            }
            WorkException workExc = new WorkCompletedException(exc);
            workExc.setErrorCode(errorCode);
            throw workExc;
        }
    }

    /**
     * Provides a handle to a <code>XATerminator</code> instance. The <code>XATerminator</code> instance could be used by a
     * resource adapter to flow-in transaction completion and crash recovery calls from an EIS.
     *
     * @return a <code>XATerminator</code> instance.
     */
    public static jakarta.resource.spi.XATerminator getXATerminator() {
        return new XATerminatorImpl();
    }

    // START IASRI 4706150
    /**
     * used to set XAResource timeout
     */
    public static void setXAResourceTimeOut(int value) {
        xaTimeOut = value;
    }

    public static int getXAResourceTimeOut() {
        return xaTimeOut;
    }
    // END IASRI 4706150
    /**
     * class SynchronizationListener implements Synchronization {
     *
     * private GlobalTID gtid; private TransactionState tranState;
     *
     * SynchronizationListener(GlobalTID gtid, TransactionState tranState) { this.gtid = gtid; this.tranState = tranState; }
     *
     * public void afterCompletion(int status) { tranState.cleanupTransactionStateMapping(); }
     *
     * public void beforeCompletion() { try { tranState.beforeCompletion(); }catch(XAException xaex){
     * _logger.log(Level.WARNING,"jts.unexpected_xa_error_in_beforecompletion", new java.lang.Object[] {xaex.errorCode,
     * xaex.getMessage()}); _logger.log(Level.WARNING,"",xaex); } catch (Exception ex) {
     * _logger.log(Level.WARNING,"jts.unexpected_error_in_beforecompletion",ex); } } }
     **/

    /**
     * void cleanupTransactionState(GlobalTID gtid) { transactionStates.remove(gtid); }
     **/
}
