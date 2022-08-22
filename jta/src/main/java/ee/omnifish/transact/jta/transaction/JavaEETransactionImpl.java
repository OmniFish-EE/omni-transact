/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
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

package ee.omnifish.transact.jta.transaction;

import static jakarta.transaction.Status.STATUS_ACTIVE;
import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_COMMITTING;
import static jakarta.transaction.Status.STATUS_MARKED_ROLLBACK;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import ee.omnifish.transact.api.JavaEETransaction;
import ee.omnifish.transact.api.JavaEETransactionManager;
import ee.omnifish.transact.api.SimpleResource;
import ee.omnifish.transact.api.spi.TransactionInternal;
import ee.omnifish.transact.api.spi.TransactionalResource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;

/**
 * This class implements the Jakarta Transaction API. It is a wrapper over the JTS Transaction object that
 * provides optimized local transaction support when a transaction uses zero/one non-XA resource, and delegates to JTS
 * otherwise.
 *
 * <p>
 * This object can be in two states: local tx <code>(jtsTx == null)</code> or global (JTS) tx. If <code>jtsTx != null</code>, all calls are
 * delegated to <code>jtsTx</code>.
 *
 * <p>
 * Time out capability is added to the local transactions. This class extends the TimerTask. When the transaction needs
 * to be timedout, this schedules with the timer. At the commit and rollback time, task will be cancelled. If the
 * transaction is timedout, the <code>run()</code> method will be called and transaction will be marked for rollback.
 */
public final class JavaEETransactionImpl extends TimerTask implements JavaEETransaction {

    protected Logger _logger = Logger.getLogger(JavaEETransactionImpl.class.getName());

    JavaEETransactionManager javaEETM;

    // Local Tx ids are just numbers: they dont need to be unique across
    // processes or across multiple activations of this server process.
    private static long txIdCounter = 1;

    // Fall back to the old (wrong) behavior for the case when setRollbackOnly
    // was called before XA transaction started
    private static boolean DISABLE_STATUS_CHECK_ON_SWITCH_TO_XA = Boolean.getBoolean("com.sun.jts.disable_status_check_on_switch_to_xa");

    private long txId;
    private JavaEEXid xid;
    private TransactionInternal jtsTx;
    private TransactionalResource nonXAResource;
    private TransactionalResource laoResource;
    private int localTxStatus;
    private Vector<Synchronization> syncs = new Vector<>();
    private Vector<Synchronization> interposedSyncs = new Vector<>();
    private boolean commitStarted;
    private long startTime;

    private boolean timedOut;
    private boolean isTimerTask;
    private int timeout = 0;

    private boolean imported;

    private Map<Object, Set> resourceTable;
    private Map<Object, Object> userResourceMap;

    // This cache contains the EntityContexts in this Tx
    private Object activeTxCache;

    // SimpleResource mapping for EMs with TX persistent context type
    private Map<EntityManagerFactory, SimpleResource> txEntityManagerMap;

    // SimpleResource mapping for EMs with EXTENDED persistence context type
    private Map<EntityManagerFactory, SimpleResource> extendedEntityManagerMap;
    private String componentName;
    private ArrayList<String> resourceNames;

    // tx-specific ejb container info associated with this tx
    private Object containerData;

    static private boolean isTimerInitialized;
    static private Timer timer;
    static private long timerTasksScheduled = 0; // Global counter

    static synchronized private void initializeTimer() {
        if (isTimerInitialized) {
            return;
        }

        timer = new Timer(true); // daemon
        isTimerInitialized = true;
    }

    JavaEETransactionImpl(JavaEETransactionManager javaEETM) {
        this.javaEETM = javaEETM;

        txId = getNewTxId();
        xid = new JavaEEXid(txId);
        resourceTable = new HashMap<>();
        localTxStatus = STATUS_ACTIVE;
        startTime = System.currentTimeMillis();

        if (_logger != null) {
            _logger.log(FINE, () -> "--Created new JavaEETransactionImpl, txId = " + txId);
        }
    }

    JavaEETransactionImpl(int timeout, JavaEETransactionManager javaEETM) {
        this(javaEETM);
        if (!isTimerInitialized) {
            initializeTimer();
        }

        timer.schedule(this, timeout * 1000L);
        timerTasksScheduled++;
        isTimerTask = true;
        this.timeout = timeout;
    }

    JavaEETransactionImpl(TransactionInternal jtsTx, JavaEETransactionManager javaEETM) {
        this(javaEETM);
        this.jtsTx = jtsTx;
        imported = true;
    }

    // TimerTask run() method implementation
    @Override
    public void run() {
        timedOut = true;
        try {
            setRollbackOnly();
        } catch (Exception e) {
            _logger.log(WARNING, "enterprise_distributedtx.some_excep", e);
        }
    }

    @Override
    public Object getContainerData() {
        return containerData;
    }

    @Override
    public void setContainerData(Object data) {
        containerData = data;
    }

    @Override
    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public TransactionalResource getNonXAResource() {
        return nonXAResource;
    }

    void setNonXAResource(TransactionalResource h) {
        nonXAResource = h;
    }

    @Override
    public TransactionalResource getLAOResource() {
        return laoResource;
    }

    @Override
    public void setLAOResource(TransactionalResource transactionalResource) {
        laoResource = transactionalResource;
    }

    @Override
    public void addTxEntityManagerMapping(EntityManagerFactory entityManagerFactory, SimpleResource em) {
        getTxEntityManagerMap().put(entityManagerFactory, em);
    }

    @Override
    public SimpleResource getTxEntityManagerResource(EntityManagerFactory entityManagerFactory) {
        return getTxEntityManagerMap().get(entityManagerFactory);
    }

    @Override
    public void addExtendedEntityManagerMapping(EntityManagerFactory entityManagerFactory, SimpleResource em) {
        getExtendedEntityManagerMap().put(entityManagerFactory, em);
    }

    @Override
    public void removeExtendedEntityManagerMapping(EntityManagerFactory entityManagerFactory) {
        getExtendedEntityManagerMap().remove(entityManagerFactory);
    }

    @Override
    public SimpleResource getExtendedEntityManagerResource(EntityManagerFactory entityManagerFactory) {
        return getExtendedEntityManagerMap().get(entityManagerFactory);
    }

    @Override
    public boolean isLocalTx() {
        return jtsTx == null;
    }

    @Override
    public boolean enlistResource(XAResource xaRes) throws RollbackException, IllegalStateException, SystemException {
        _logger.log(FINE, () -> "--In JavaEETransactionImpl.enlistResource, jtsTx=" + jtsTx + " nonXAResource=" + nonXAResource);
        checkTransationActive();

        if (!isLocalTx()) {
            return jtsTx.enlistResource(xaRes);
        }

        if (nonXAResource != null) {
            throw new IllegalStateException("Local transaction already has 1 non-XA Resource: cannot add more resources.");
        }

        /***
         * else // V2-XXX what to do ? Start a new JTS tx ? throw new
         * IllegalStateException("JavaEETransactionImpl.enlistResource called for local tx");
         ***/
        // Start a new JTS tx
        ((JavaEETransactionManagerImpl) javaEETM).startJTSTx(this);
        return jtsTx.enlistResource(xaRes);

    }

    @Override
    public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException {
        _logger.log(FINE, () -> "--In JavaEETransactionImpl.delistResource: " + xaRes + " from " + this);
        checkTransationActive();

        if (isLocalTx()) {
            throw new IllegalStateException("JavaEETransaction.delistResource called for local tx");
        }

        return jtsTx.delistResource(xaRes, flag);
    }

    @Override
    public int getStatus() throws SystemException {
        if (isLocalTx()) {
            return localTxStatus;
        }

        return jtsTx.getStatus();
    }

    @Override
    public void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
        _logger.log(FINE, () ->
                "--In JavaEETransactionImpl.registerSynchronization, jtsTx=" + jtsTx + " nonXAResource=" + nonXAResource);

        checkTransationActive();

        if (isLocalTx()) {
            syncs.add(sync);
        } else {
            jtsTx.registerSynchronization(sync);
        }
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        checkTransationActive();

        if (isLocalTx()) {
            localTxStatus = STATUS_MARKED_ROLLBACK;
        } else {
            jtsTx.setRollbackOnly();
        }
    }

    @Override
    public void setResources(Set resources, Object poolInfo) {
        resourceTable.put(poolInfo, resources);
    }

    @Override
    public Set getResources(Object poolInfo) {
        return resourceTable.get(poolInfo);
    }

    /**
     * Return all pools registered in the resourceTable. This will cut down the scope of pools on which transactionComplted
     * is called by the PoolManagerImpl. This method will return only those pools that have ever participated in a tx
     */
    @Override
    public Set getAllParticipatingPools() {
        return resourceTable.keySet();
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        checkTransationActive();

        // If this transaction is set for timeout, cancel it as it is in the commit state
        if (isTimerTask) {
            cancelTimerTask();
        }

        _logger.log(FINE, () -> "--In JavaEETransactionImpl.commit, jtsTx=" + jtsTx + " nonXAResource=" + nonXAResource);

        commitStarted = true;
        boolean success = false;
        if (!isLocalTx()) {
            try {
                jtsTx.commit();
                success = true;
            } catch (HeuristicMixedException e) {
                success = true;
                throw e;
            } finally {
                completeMonitorTx(success);
                onTxCompletion(success);
                try {
                    localTxStatus = jtsTx.getStatus();
                } catch (Exception e) {
                    localTxStatus = STATUS_NO_TRANSACTION;
                }

                jtsTx = null;
            }

        } else { // local tx
            Exception caughtException = null;
            try {
                if (timedOut) {
                    // rollback nonXA resource
                    if (nonXAResource != null) {
                        nonXAResource.getXAResource().rollback(xid);
                    }
                    localTxStatus = STATUS_ROLLEDBACK;
                    throw new RollbackException("Transaction rolled back due to time out.");
                }

                if (isRollbackOnly()) {
                    // rollback nonXA resource
                    if (nonXAResource != null) {
                        nonXAResource.getXAResource().rollback(xid);
                    }

                    localTxStatus = STATUS_ROLLEDBACK;
                    throw new RollbackException("Transaction marked for rollback.");
                }

                // call beforeCompletion
                for (Synchronization synchronization : syncs) {
                    try {
                        synchronization.beforeCompletion();
                    } catch (RuntimeException ex) {
                        _logger.log(WARNING, "DTX5014: Caught exception in beforeCompletion() callback:", ex);
                        setRollbackOnly();
                        caughtException = ex;
                        break;
                    } catch (Exception ex) {
                        _logger.log(WARNING, "DTX5014: Caught exception in beforeCompletion() callback:", ex);
                        // XXX-V2 no setRollbackOnly() ???
                    }
                }

                for (Synchronization synchronization : interposedSyncs) {
                    try {
                        synchronization.beforeCompletion();
                    } catch (RuntimeException ex) {
                        _logger.log(WARNING, "DTX5014: Caught exception in beforeCompletion() callback:", ex);
                        setRollbackOnly();
                        caughtException = ex;
                        break;
                    } catch (Exception ex) {
                        _logger.log(WARNING, "DTX5014: Caught exception in beforeCompletion() callback:", ex);
                        // XXX-V2 no setRollbackOnly() ???
                    }

                }

                // Check rollbackonly again, in case any of the beforeCompletion
                // calls marked it for rollback.
                if (isRollbackOnly()) {
                    // Check if it is a Local Transaction
                    RollbackException rollbackException = null;
                    if (isLocalTx()) {
                        if (nonXAResource != null) {
                            nonXAResource.getXAResource().rollback(xid);
                        }
                        localTxStatus = STATUS_ROLLEDBACK;
                        rollbackException = new RollbackException("Transaction marked for rollback.");

                        // else it is a global transaction
                    } else {
                        jtsTx.rollback();
                        localTxStatus = STATUS_ROLLEDBACK;
                        rollbackException = new RollbackException("Transaction marked for rollback.");
                    }

                    // RollbackException doesn't have a constructor that takes a Throwable.
                    if (caughtException != null) {
                        rollbackException.initCause(caughtException);
                    }

                    throw rollbackException;
                }

                // Check if there is a jtsTx active, in case any of the
                // beforeCompletions registered the first XA resource.
                if (!isLocalTx()) {
                    jtsTx.commit();

                    // Note: JTS will not call afterCompletions in this case,
                    // because no syncs have been registered with JTS.
                    // So afterCompletions are called in finally block below.

                } else  if (nonXAResource != null) {
                    // Do single-phase commit on nonXA resource
                    nonXAResource.getXAResource().commit(xid, true);
                }
                // V2-XXX should this be STATUS_NO_TRANSACTION ?
                localTxStatus = STATUS_COMMITTED;
                success = true;

            } catch (RollbackException ex) {
                localTxStatus = STATUS_ROLLEDBACK; // V2-XXX is this correct ?
                throw ex;

            } catch (SystemException ex) {
                localTxStatus = STATUS_COMMITTING; // V2-XXX is this correct ?
                success = true;
                throw ex;

            } catch (Exception ex) {
                localTxStatus = STATUS_ROLLEDBACK; // V2-XXX is this correct ?
                SystemException exc = new SystemException();
                exc.initCause(ex);
                throw exc;

            } finally {
                completeMonitorTx(success);

                for (Synchronization synchronization : interposedSyncs) {
                    try {
                        synchronization.afterCompletion(localTxStatus);
                    } catch (Exception ex) {
                        _logger.log(WARNING, "enterprise_distributedtx.after_completion_excep", ex);
                    }
                }

                // Call afterCompletions
                for (Synchronization synchronization : syncs) {
                    try {
                        synchronization.afterCompletion(localTxStatus);
                    } catch (Exception ex) {
                        _logger.log(WARNING, "enterprise_distributedtx.after_completion_excep", ex);
                    }
                }

                onTxCompletion(success);
                jtsTx = null;
            }
        }
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {
        // If this transaction is set for timeout, cancel it as it is in the rollback state
        if (isTimerTask) {
            cancelTimerTask();
        }

        _logger.log(FINE, () -> "--In JavaEETransactionImpl.rollback, jtsTx=" + jtsTx + " nonXAResource=" + nonXAResource);

        if (isLocalTx()) {
            checkTransationActive(); // non-xa transaction can't be in prepared state, xa code will do its check
        }

        try {
            if (!isLocalTx()) {
                jtsTx.rollback();
            } else if (nonXAResource != null) {
                nonXAResource.getXAResource().rollback(xid);
            }

        } catch (SystemException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            _logger.log(WARNING, "enterprise_distributedtx.some_excep", ex);
        } finally {
            // V2-XXX should this be STATUS_NO_TRANSACTION ?
            localTxStatus = STATUS_ROLLEDBACK;

            completeMonitorTx(false);

            if (isLocalTx()) {
                for (Synchronization synchronization : interposedSyncs) {
                    try {
                        synchronization.afterCompletion(STATUS_ROLLEDBACK);
                    } catch (Exception ex) {
                        _logger.log(WARNING, "enterprise_distributedtx.after_completion_excep", ex);
                    }
                }

                // Call afterCompletions
                for (Synchronization sync : syncs) {
                    try {
                        sync.afterCompletion(STATUS_ROLLEDBACK);
                    } catch (Exception ex) {
                        _logger.log(WARNING, "enterprise_distributedtx.after_completion_excep", ex);
                    }

                }

            }
            onTxCompletion(false);
            jtsTx = null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other instanceof JavaEETransactionImpl) {
            JavaEETransactionImpl othertx = (JavaEETransactionImpl) other;
            return (txId == othertx.txId);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return (int) txId;
    }

    @Override
    public String toString() {
        return
            "JavaEETransactionImpl: txId=" + txId +
             " nonXAResource=" + nonXAResource +
             " jtsTx=" + jtsTx +
             " localTxStatus=" + localTxStatus +
             " syncs=" + syncs;
    }



    // ## Public non-interface methods


    /*
     * This method is used for the Admin Framework displaying of Transactions Ids
     */
    public String getTransactionId() {
        return xid.toString();
    }

    /*
     * This method returns the time this transaction was started
     */
    public long getStartTime() {
        return startTime;
    }

    public void setActiveTxCache(Object cache) {
        this.activeTxCache = cache;
    }

    public Object getActiveTxCache() {
        return this.activeTxCache;
    }

    /**
     * Return duration in seconds before transaction would timeout.
     *
     * Returns zero if this transaction has no timeout set. Returns negative value if already timed out.
     */
    public int getRemainingTimeout() {
        if (timeout == 0) {
            return timeout;
        }

        if (timedOut) {
            return -1;
        }

        // Compute how much time left before transaction times out
        return timeout - (int) ((System.currentTimeMillis() - startTime) / 1000L);
    }

    // Cancels the timertask and returns the timeout
    public int cancelTimerTask() {
        cancel();

        int mod = javaEETM.getPurgeCancelledTtransactionsAfter();
        if (mod > 0 && timerTasksScheduled % mod == 0) {
            int purged = timer.purge();
            if (_logger.isLoggable(FINE)) {
                _logger.log(FINE, "Purged " + purged + " timer tasks from canceled queue");
            }
        }

        return timeout;
    }

    Xid getLocalXid() {
        return xid;
    }


    void setJTSTx(TransactionInternal jtsTx) throws RollbackException, SystemException {
        // Remember the status from this transaction
        boolean markedForRollback = isRollbackOnly();

        this.jtsTx = jtsTx;

        if (!commitStarted) {
            // Register syncs
            for (Synchronization synchronization : syncs) {
                jtsTx.registerSynchronization(synchronization);
            }

            for (Synchronization synchronization : interposedSyncs) {
                jtsTx.registerInterposedSynchronization(synchronization);
            }
        }

        // Now adjust the status
        if (!DISABLE_STATUS_CHECK_ON_SWITCH_TO_XA && markedForRollback) {
            jtsTx.setRollbackOnly();
        }
    }

    TransactionInternal getJTSTx() {
        return jtsTx;
    }

    boolean isAssociatedTimeout() {
        return isTimerTask;
    }

    protected void onTxCompletion(boolean status) {
        if (txEntityManagerMap == null) {
            return;
        }

        for (Map.Entry<EntityManagerFactory, SimpleResource> entry : getTxEntityManagerMap().entrySet()) {

            SimpleResource entityManagerResource = entry.getValue();
            if (entityManagerResource.isOpen()) {
                try {
                    entityManagerResource.close();
                } catch (Throwable th) {
                    _logger.log(FINE, "Exception while closing em.", th);
                }
            }
        }
    }

    boolean isImportedTransaction() {
        return imported;
    }

    synchronized void putUserResource(Object key, Object value) {
        if (userResourceMap == null) {
            userResourceMap = new HashMap<>();
        }
        userResourceMap.put(key, value);
    }

    synchronized Object getUserResource(Object key) {
        if (userResourceMap == null) {
            return null;
        }
        return userResourceMap.get(key);
    }

    public void registerInterposedSynchronization(Synchronization sync) throws RollbackException, SystemException {
        interposedSyncs.add(sync);
        if (jtsTx != null) {
            jtsTx.registerInterposedSynchronization(sync);
        }
    }

    void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    String getComponentName() {
        return componentName;
    }

    synchronized void addResourceName(String resourceName) {
        if (resourceNames == null) {
            resourceNames = new ArrayList<>();
        }
        if (!resourceNames.contains(resourceName)) {
            resourceNames.add(resourceName);
        }
    }

    synchronized ArrayList<String> getResourceNames() {
        return resourceNames;
    }



    // ## Private methods

    private boolean isRollbackOnly() throws IllegalStateException, SystemException {
        return getStatus() == STATUS_MARKED_ROLLBACK;
    }

    private void checkTransationActive() throws SystemException {
        int status = getStatus();
        if (status != STATUS_MARKED_ROLLBACK && status != STATUS_ACTIVE) {
            throw new IllegalStateException("JavaEETransaction.delistResource called for local tx");
        }
    }

    private Map<EntityManagerFactory, SimpleResource> getExtendedEntityManagerMap() {
        if (extendedEntityManagerMap == null) {
            extendedEntityManagerMap = new HashMap<>();
        }

        return extendedEntityManagerMap;
    }

    private static synchronized long getNewTxId() {
        long newTxId = txIdCounter++;
        return newTxId;
    }

    private Map<EntityManagerFactory, SimpleResource> getTxEntityManagerMap() {
        if (txEntityManagerMap == null) {
            txEntityManagerMap = new HashMap<>();
        }
        return txEntityManagerMap;
    }

    private JavaEETransactionManagerImpl getJavaEETransactionManagerSimplified() {
        return (JavaEETransactionManagerImpl) javaEETM;
    }

    private void completeMonitorTx(boolean committed) {
        getJavaEETransactionManagerSimplified().monitorTxCompleted(this, committed);
        getJavaEETransactionManagerSimplified().clearThreadTx();
    }



    // Assume that there is only one instance of this class per local tx.
    private static class JavaEEXid implements javax.transaction.xa.Xid {
        private static final int formatId = 987654321;
        private static final byte[] bqual = new byte[] { 0 };

        private byte[] gtrId;
        private String stringForm;

        JavaEEXid(long txId) {
            gtrId = new byte[8];
            longToBytes(txId, gtrId, 0);
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return gtrId;
        }

        @Override
        public byte[] getBranchQualifier() {
            return bqual; // V2-XXX check if its ok to always have same bqual
        }

        /**
         * Marshal an long to a byte array. The bytes are in BIGENDIAN order. i.e. array[offset] is the most-significant-byte
         * and array[offset+7] is the least-significant-byte.
         *
         * @param array The array of bytes.
         * @param offset The offset from which to start marshalling.
         */
        public void longToBytes(long value, byte[] array, int offset) {
            array[offset++] = (byte) ((value >>> 56) & 0xFF);
            array[offset++] = (byte) ((value >>> 48) & 0xFF);
            array[offset++] = (byte) ((value >>> 40) & 0xFF);
            array[offset++] = (byte) ((value >>> 32) & 0xFF);
            array[offset++] = (byte) ((value >>> 24) & 0xFF);
            array[offset++] = (byte) ((value >>> 16) & 0xFF);
            array[offset++] = (byte) ((value >>> 8) & 0xFF);
            array[offset++] = (byte) ((value >>> 0) & 0xFF);
        }

        /*
         * returns the Transaction id of this transaction
         */
        @Override
        public String toString() {

            // If we have a cached copy of the string form of the global identifier, return
            // it now.
            if (stringForm != null) {
                return stringForm;
            }

            // Otherwise format the global identifier.
            // char[] buff = new char[gtrId.length*2 + 2/*'[' and ']'*/ + 3/*bqual and ':'*/];
            char[] buff = new char[gtrId.length * 2 + 3/* bqual and ':' */];
            int pos = 0;
            // buff[pos++] = '[';

            // Convert the global transaction identifier into a string of hex digits.

            int globalLen = gtrId.length;
            for (int i = 0; i < globalLen; i++) {
                int currCharHigh = (gtrId[i] & 0xf0) >> 4;
                int currCharLow = gtrId[i] & 0x0f;
                buff[pos++] = (char) (currCharHigh + (currCharHigh > 9 ? 'A' - 10 : '0'));
                buff[pos++] = (char) (currCharLow + (currCharLow > 9 ? 'A' - 10 : '0'));
            }

            // buff[pos++] = ':';
            buff[pos++] = '_';
            int currCharHigh = (0 & 0xf0) >> 4;
            int currCharLow = 0 & 0x0f;
            buff[pos++] = (char) (currCharHigh + (currCharHigh > 9 ? 'A' - 10 : '0'));
            buff[pos++] = (char) (currCharLow + (currCharLow > 9 ? 'A' - 10 : '0'));
            // buff[pos] = ']';

            // Cache the string form of the global identifier.
            stringForm = new String(buff);

            return stringForm;
        }
    }

}
