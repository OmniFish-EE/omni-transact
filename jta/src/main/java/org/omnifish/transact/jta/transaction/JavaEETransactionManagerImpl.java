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

package org.omnifish.transact.jta.transaction;

import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static jakarta.transaction.Status.STATUS_UNKNOWN;
import static java.util.Collections.synchronizedList;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.omnifish.transact.jta.cache.BaseCache;
import org.omnifish.transact.jta.cache.Cache;

import com.sun.enterprise.transaction.api.ComponentInvocation;
import com.sun.enterprise.transaction.api.InvocationException;
import com.sun.enterprise.transaction.api.InvocationManager;
import com.sun.enterprise.transaction.api.JavaEETransaction;
import com.sun.enterprise.transaction.api.JavaEETransactionManager;
import com.sun.enterprise.transaction.api.ResourceHandler;
import com.sun.enterprise.transaction.api.TransactionAdminBean;
import com.sun.enterprise.transaction.api.TransactionServiceConfig;
import com.sun.enterprise.transaction.api.XAResourceWrapper;
import com.sun.enterprise.transaction.spi.JavaEETransactionManagerDelegate;
import com.sun.enterprise.transaction.spi.ServiceLocator;
import com.sun.enterprise.transaction.spi.TransactionInternal;
import com.sun.enterprise.transaction.spi.TransactionalResource;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.work.WorkException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;

/**
 * Implementation of jakarta.transaction.TransactionManager interface. This class provides non-XA local transaction
 * support and delegates to implementation of the JavaEETransactionManagerDelegate for XA or LAO optimization, and
 * complete JTS implementation.
 *
 * @author Tony Ng
 * @author Marina Vatkina
 */
@ApplicationScoped
@Typed(JavaEETransactionManager.class)
public class JavaEETransactionManagerImpl implements JavaEETransactionManager {

    protected Logger _logger = Logger.getLogger(JavaEETransactionManagerImpl.class.getName());

    private static final Hashtable<Integer, String> statusMap = new Hashtable<>();

    // Note: this is not inheritable because we dont want transactions
    // to be inherited by child threads.
    private ThreadLocal<JavaEETransaction> threadLocalTransactionHolder;
    private ThreadLocal<int[]> threadLocalCallCounterHolder;
    private ThreadLocal<JavaEETransactionManagerDelegate> threadLocalDelegateHolder;
    private ThreadLocal<Integer> txnTmout = new ThreadLocal<>();

    @Inject
    private ServiceLocator serviceLocator;

    @Inject
    protected InvocationManager invocationManager;

    private JavaEETransactionManagerDelegate instanceDelegate;

    // If multipleEnlistDelists is set to true, with in the transaction, for the same
    // - connection multiple enlistments and delistments might happen
    // - By setting the System property ALLOW_MULTIPLE_ENLISTS_DELISTS to true
    // - multipleEnlistDelists can be enabled
    private boolean multipleEnlistDelists;

    private int transactionTimeout;
    private int purgeCancelledTtransactions;

    // admin and monitoring related parameters
    private List<Transaction> activeTransactions = synchronizedList(new ArrayList<>());
    private boolean monitoringEnabled;

    // private TransactionServiceProbeProvider monitor;
    private Hashtable<String, Transaction> txnTable;

    private Cache resourceTable;

    private Timer _timer = new Timer("transaction-manager", true);

    static {
        statusMap.put(Status.STATUS_ACTIVE, "Active");
        statusMap.put(Status.STATUS_MARKED_ROLLBACK, "MarkedRollback");
        statusMap.put(Status.STATUS_PREPARED, "Prepared");
        statusMap.put(STATUS_COMMITTED, "Committed");
        statusMap.put(STATUS_ROLLEDBACK, "RolledBack");
        statusMap.put(STATUS_UNKNOWN, "UnKnown");
        statusMap.put(STATUS_NO_TRANSACTION, "NoTransaction");
        statusMap.put(Status.STATUS_PREPARING, "Preparing");
        statusMap.put(Status.STATUS_COMMITTING, "Committing");
        statusMap.put(Status.STATUS_ROLLING_BACK, "RollingBack");
    }

    public JavaEETransactionManagerImpl() {
        threadLocalTransactionHolder = new ThreadLocal<>();
        threadLocalCallCounterHolder = new ThreadLocal<>();
        threadLocalDelegateHolder = new ThreadLocal<>();
    }

    @PostConstruct
    public void postConstruct() {
        initDelegates();
        initProperties();
    }

    private void initProperties() {
        int maxEntries = 8192; // FIXME: this maxEntry should be a config
        float loadFactor = 0.75f; // FIXME: this loadFactor should be a config

        // For now, let's get it from system prop
        try {
            String mEnlistDelists = System.getProperty("ALLOW_MULTIPLE_ENLISTS_DELISTS");
            if ("true".equals(mEnlistDelists)) {
                multipleEnlistDelists = true;
                if (_logger.isLoggable(FINE)) {
                    _logger.log(FINE, "TM: multiple enlists, delists are enabled");
                }
            }
            String maxEntriesValue = System.getProperty("JTA_RESOURCE_TABLE_MAX_ENTRIES");
            if (maxEntriesValue != null) {
                int temp = Integer.parseInt(maxEntriesValue);
                if (temp > 0) {
                    maxEntries = temp;
                }
            }
            String loadFactorValue = System.getProperty("JTA_RESOURCE_TABLE_DEFAULT_LOAD_FACTOR");
            if (loadFactorValue != null) {
                float f = Float.parseFloat(loadFactorValue);
                if (f > 0) {
                    loadFactor = f;
                }
            }
        } catch (Exception ex) {
            // ignore
        }

        resourceTable = new BaseCache();
        ((BaseCache) resourceTable).init(maxEntries, loadFactor, null);

        if (serviceLocator != null) {
            TransactionServiceConfig txnService = serviceLocator.getService(TransactionServiceConfig.class, "default-instance-name");

            // running on the server side ?
            if (txnService != null) {
                transactionTimeout = Integer.parseInt(txnService.getTimeoutInSeconds());
                // the delegates will do the rest if they support it

                String v = txnService.getPropertyValue("purge-cancelled-transactions-after");
                if (v != null && v.length() > 0) {
                    purgeCancelledTtransactions = Integer.parseInt(v);
                }

                // TODO: listener
                // TransactionServiceConfigListener listener = habitat.getService(TransactionServiceConfigListener.class);
                // listener.setTM(this);
            }

            // TODO: monitor

//            ModuleMonitoringLevels levels = habitat.getService(ModuleMonitoringLevels.class);
//            // running on the server side ?
//            if (levels != null) {
//                String level = levels.getTransactionService();
//                if (!("OFF".equals(level))) {
//                    monitoringEnabled = true;
//                }
//            }
        }

        _logger.log(FINE, () -> "TM: Tx Timeout = " + transactionTimeout);

        // Monitor resource table stats
        try {
            // XXX TODO:
            if (Boolean.getBoolean("MONITOR_JTA_RESOURCE_TABLE_STATISTICS")) {
                registerStatisticMonitorTask();
            }

//            StatsProviderManager.register("transaction-service", // element in domain.xml <monitoring-service>/<monitoring-level>
//                    PluginPoint.SERVER, "transaction-service", // server.transaction-service node in asadmin get
//
//                    null
//                    // new TransactionServiceStatsProvider(this, _logger)
//
//
//                    );
        } catch (Exception ex) {
            // ignore
        }

        // monitor = new TransactionServiceProbeProvider();
    }

    @Override
    public void clearThreadTx() {
        setCurrentTransaction(null);
        threadLocalDelegateHolder.set(null);
    }

    @Override
    public String getTxLogLocation() {
        return getDelegate().getTxLogLocation();
    }

    @Override
    public void registerRecoveryResourceHandler(XAResource xaResource) {
        getDelegate().registerRecoveryResourceHandler(xaResource);
    }



    /****************************************************************************/
    /** Implementations of JavaEETransactionManager APIs **************************/
    /****************************************************************************/

    /**
     * Return true if a "null transaction context" was received from the client. See EJB2.0 spec section 19.6.2.1. A null tx
     * context has no Coordinator objref. It indicates that the client had an active tx but the client container did not
     * support tx interop.
     */
    @Override
    public boolean isNullTransaction() {
        return getDelegate().isNullTransaction();
    }

    @Override
    public void shutdown() {
        _timer.cancel();
    }

    @Override
    public void initRecovery(boolean force) {
        getDelegate().initRecovery(force);
    }

    @Override
    public void recover(XAResource[] resourceList) {
        getDelegate().recover(resourceList);
    }

    @Override
    public boolean enlistResource(Transaction transaction, TransactionalResource transactionalResource) throws RollbackException, IllegalStateException, SystemException {
        _logger.log(FINE, () ->
            "\n\nIn JavaEETransactionManagerSimplified.enlistResource, h=" + transactionalResource +
            " h.xares=" + transactionalResource.getXAResource() +
            " tran=" + transaction);

        if (!transactionalResource.isTransactional()) {
            return true;
        }

        // If LazyEnlistment is suspended, do not enlist resource.
        if (transactionalResource.isEnlistmentSuspended()) {
            return false;
        }

        if (monitoringEnabled) {
            JavaEETransaction eeTransaction = getDelegate().getJavaEETransaction(transaction);
            if (eeTransaction != null) {
                ((JavaEETransactionImpl) eeTransaction).addResourceName(transactionalResource.getName());
            }
        }

        if (!(transaction instanceof JavaEETransaction)) {
            return enlistXAResource(transaction, transactionalResource);
        }

        JavaEETransactionImpl eeTransaction = (JavaEETransactionImpl) transaction;

        JavaEETransactionManagerDelegate eeTransactionManagerDelegate = setDelegate();
        boolean useLAO = eeTransactionManagerDelegate.useLAO();

        if ((eeTransaction.getNonXAResource() != null) && (!useLAO || !transactionalResource.supportsXA())) {
            boolean isSameRM = false;
            try {
                isSameRM = transactionalResource.getXAResource().isSameRM(eeTransaction.getNonXAResource().getXAResource());
                if (_logger.isLoggable(FINE)) {
                    _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.enlistResource, isSameRM? " + isSameRM);
                }
            } catch (Exception ex) {
                throw new SystemException("enterprise_distributedtx.samerm_excep" + ex);
            }

            if (!isSameRM) {
                throw new IllegalStateException("enterprise_distributedtx.already_has_nonxa");
            }
        }

        if (transactionalResource.supportsXA()) {
            if (!eeTransactionManagerDelegate.supportsXAResource()) {
                throw new IllegalStateException("enterprise_distributedtx.xaresource_not_supported");
            }

            if (eeTransaction.isLocalTx()) {
                eeTransactionManagerDelegate.enlistLAOResource(eeTransaction, eeTransaction.getNonXAResource());

                /**
                 * XXX TO BE MOVED TO XA DELEGATE XXX ** startJTSTx(tx);
                 *
                 * //If transaction conatains a NonXA and no LAO, convert the existing //Non XA to LAO if(useLAO) {
                 * if(tx.getNonXAResource()!=null && (tx.getLAOResource()==null) ) { tx.setLAOResource(tx.getNonXAResource()); // XXX
                 * super.enlistLAOResource(tx, tx.getNonXAResource()); } } XXX TO BE MOVED TO XA DELEGATE XXX
                 **/
            }
            return enlistXAResource(eeTransaction, transactionalResource);
        }

        // Non-XA resource
        if (eeTransaction.isImportedTransaction()) {
            throw new IllegalStateException("enterprise_distributedtx.nonxa_usein_jts");
        }

        if (eeTransaction.getNonXAResource() == null) {
            eeTransaction.setNonXAResource(transactionalResource);
        }

        if (eeTransaction.isLocalTx()) {
            // Notify resource that it is being used for tx, e.g. this allows the correct physical
            // connection to be swapped in for the logical connection.
            //
            // The flags parameter can be 0 because the flags are not used by the XAResource
            // implementation for non-XA resources.
            try {
                transactionalResource.getXAResource().start(eeTransaction.getLocalXid(), 0);
            } catch (XAException ex) {
                throw new RuntimeException("enterprise_distributedtx.xaresource_start_excep" + ex);
            }

            transactionalResource.enlistedInTransaction(eeTransaction);
            return true;
        }

        return eeTransactionManagerDelegate.enlistDistributedNonXAResource(eeTransaction, transactionalResource);
        /**
         * XXX TO BE MOVED TO XA DELEGATE? XXX if(useLAO) { return super.enlistResource(tx, h); } else { throw new
         * IllegalStateException( sm.getString("enterprise_distributedtx.nonxa_usein_jts")); } XXX TO BE MOVED TO XA DELEGATE?
         * XXX
         **/
    }

    @Override
    public void unregisterComponentResource(TransactionalResource transactionalResource) {
        _logger.log(FINE, () ->
            "\n\nIn JavaEETransactionManagerSimplified.unregisterComponentResource, h=" + transactionalResource +
            " h.xares=" + transactionalResource.getXAResource());

        Object instance = transactionalResource.getComponentInstance();
        if (instance == null) {
            return;
        }

        transactionalResource.setComponentInstance(null);
        ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();
        List<TransactionalResource> existingResourceList = getExistingResourceList(instance, componentInvocation);

        if (existingResourceList != null) {
            existingResourceList.remove(transactionalResource);
        }
    }

    public void startJTSTx(JavaEETransaction t) throws RollbackException, IllegalStateException, SystemException {
        JavaEETransactionImpl eeTransaction = (JavaEETransactionImpl) t;
        TransactionInternal jtsTx = getDelegate().startJTSTx(eeTransaction, eeTransaction.isAssociatedTimeout());

        // The local Transaction was promoted to global Transaction
        if (monitoringEnabled) {
            if (activeTransactions.remove(eeTransaction)) {
                // monitor.transactionDeactivatedEvent();
            }
        }

        eeTransaction.setJTSTx(jtsTx);
        jtsTx.registerSynchronization(new JTSSynchronization(jtsTx, this));
    }

    /**
     * get the resources being used in the calling component's invocation context
     *
     * @param instance Calling component instance
     * @param componentInvocation Calling component's invocation information
     * @return List of resources
     */
    @Override
    public List<TransactionalResource> getResourceList(Object instance, ComponentInvocation componentInvocation) {
        if (componentInvocation == null) {
            return new ArrayList<>(0);
        }

        List<TransactionalResource> resourceList = null;

        /**
         * XXX EJB CONTAINER ONLY XXX -- NEED TO CHECK THE NEW CODE BELOW ** if (inv.getInvocationType() ==
         * ComponentInvocation.ComponentInvocationType.EJB_INVOCATION) { ComponentContext ctx = inv.context; if (ctx != null) l
         * = ctx.getResourceList(); else { l = new ArrayList(0); } } XXX EJB CONTAINER ONLY XXX
         **/

        ResourceHandler resourceHandler = componentInvocation.getResourceHandler();
        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, () ->
                "\n\nIn JavaEETransactionManagerSimplified.getResourceList, " +
                ((resourceHandler == null) ? "" : (" ResourceHandler type: " + resourceHandler.getClass().getName())) +
                " ResourceHandler: " + resourceHandler);
        }

        if (resourceHandler != null) {
            resourceList = resourceHandler.getResourceList();
            if (resourceList == null) {
                resourceList = new ArrayList<>(0);
            }
        } else {
            Object key = getResourceTableKey(instance, componentInvocation);
            if (key == null) {
                return new ArrayList<>(0);
            }

            resourceList = (List) resourceTable.get(key);
            if (resourceList == null) {
                resourceList = new ArrayList<>();
                resourceTable.put(key, resourceList);
            }
        }

        return resourceList;
    }

    @Override
    public void enlistComponentResources() throws RemoteException {
        _logger.log(FINE, "TM: enlistComponentResources");

        ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();
        if (componentInvocation == null) {
            return;
        }

        try {
            Transaction transaction = getTransaction();
            componentInvocation.setTransaction(transaction);
            enlistComponentResources(componentInvocation);
        } catch (InvocationException ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_enlist", ex);
            throw new RemoteException(ex.getMessage(), ex.getCause());
        } catch (Exception ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_enlist", ex);
            throw new RemoteException(ex.getMessage(), ex);
        }
    }

    @Override
    public boolean delistResource(Transaction transaction, TransactionalResource transactionalResource, int flag) throws IllegalStateException, SystemException {
        _logger.log(FINE, () ->
            "\n\nIn JavaEETransactionManagerSimplified.delistResource, h=" + transactionalResource +
            " h.xares=" + transactionalResource.getXAResource() +
            " tran=" + transaction);

        if (!transactionalResource.isTransactional()) {
            return true;
        }

        if (!(transaction instanceof JavaEETransaction)) {
            return delistJTSResource(transaction, transactionalResource, flag);
        }

        JavaEETransactionImpl eeTransactionImpl = (JavaEETransactionImpl) transaction;
        if (eeTransactionImpl.isLocalTx()) {
            // Dissociate resource from tx
            try {
                transactionalResource.getXAResource().end(eeTransactionImpl.getLocalXid(), flag);
            } catch (XAException ex) {
                throw new RuntimeException("enterprise_distributedtx.xaresource_end_excep");
            }

            return true;
        }

        return delistJTSResource(transaction, transactionalResource, flag);
    }

    @Override
    public void delistComponentResources(boolean suspend) throws RemoteException {
        _logger.log(FINE, "TM: delistComponentResources");

        ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();
        if (componentInvocation == null) {
            return;
        }

        try {
            delistComponentResources(componentInvocation, suspend);
        } catch (InvocationException ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_delist", ex);
            throw new RemoteException("", ex.getCause());
        } catch (Exception ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_delist", ex);
            throw new RemoteException("", ex);
        }
    }

    @Override
    public void registerComponentResource(TransactionalResource transactionalResource) {
        ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();
        if (componentInvocation != null) {
            Object instance = componentInvocation.getInstance();
            if (instance == null) {
                return;
            }

            transactionalResource.setComponentInstance(instance);
            List<TransactionalResource> resourceList = getResourceList(instance, componentInvocation);
            _logger.log(FINE, () ->
                "\n\nIn JavaEETransactionManagerSimplified.registerComponentResource, h=" + transactionalResource +
                " h.xares=" + transactionalResource.getXAResource());

            resourceList.add(transactionalResource);
        }
    }

    private JavaEETransactionImpl initJavaEETransaction(int timeout) {
        JavaEETransactionImpl eeTransaction = null;

        // Do not need to use injection.
        if (timeout > 0) {
            eeTransaction = new JavaEETransactionImpl(timeout, this);
        } else {
            eeTransaction = new JavaEETransactionImpl(this);
        }

        setCurrentTransaction(eeTransaction);
        return eeTransaction;
    }

    @Override
    public List<TransactionalResource> getExistingResourceList(Object instance, ComponentInvocation componentInvocation) {
        if (componentInvocation == null) {
            return null;
        }

        List<TransactionalResource> resourceList = null;

        ResourceHandler resourceHandler = componentInvocation.getResourceHandler();
        _logger.log(FINE, () ->
            "\n\nIn JavaEETransactionManagerSimplified.getExistingResourceList, " +
            ((resourceHandler == null) ? "" : (" ResourceHandler type: " + resourceHandler.getClass().getName())) +
            " ResourceHandler: " + resourceHandler);

        if (resourceHandler != null) {
            resourceList = resourceHandler.getResourceList();
        } else {
            Object key = getResourceTableKey(instance, componentInvocation);
            if (key != null) {
                resourceList = (List<TransactionalResource>) resourceTable.get(key);
            }
        }

        return resourceList;
    }

    @Override
    public void preInvoke(ComponentInvocation previousInvocation) throws InvocationException {
        if (previousInvocation != null && previousInvocation.getTransaction() != null && !previousInvocation.isTransactionCompleting()) {
            // Do not worry about delisting previous invocation resources if transaction is being completed
            delistComponentResources(previousInvocation, true); // delist with TMSUSPEND
        }
    }

    @Override
    public void postInvoke(ComponentInvocation currentInvocation, ComponentInvocation previousInvocation) throws InvocationException {
        if (currentInvocation != null && currentInvocation.getTransaction() != null) {
            delistComponentResources(currentInvocation, false); // delist with TMSUCCESS
        }

        if (previousInvocation != null && previousInvocation.getTransaction() != null && !previousInvocation.isTransactionCompleting()) {
            // Do not worry about re-enlisting previous invocation resources if transaction is being completed
            enlistComponentResources(previousInvocation);
        }
    }

    @Override
    public void componentDestroyed(Object instance) {
        componentDestroyed(instance, null);
    }

    @Override
    public void componentDestroyed(Object instance, ComponentInvocation inv) {
        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "TM: componentDestroyed" + instance);
            _logger.log(FINE, "TM: resourceTable before: " + resourceTable.getEntryCount());
        }

        // Access resourceTable directly to avoid adding an empty list then removing it
        List<TransactionalResource> l = (List<TransactionalResource>) resourceTable.remove(getResourceTableKey(instance, inv));
        processResourceList(l);

        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "TM: resourceTable after: " + resourceTable.getEntryCount());
        }
    }

    @Override
    public void componentDestroyed(ResourceHandler resourceHandler) {
        _logger.log(FINE, () -> " componentDestroyed: " + resourceHandler);

        if (resourceHandler != null) {
            processResourceList(resourceHandler.getResourceList());
        }
    }

    @Override
    public boolean isTimedOut() {
        JavaEETransaction eeTransaction = threadLocalTransactionHolder.get();
        if (eeTransaction == null) {
            return false;
        }

        return eeTransaction.isTimedOut();
    }

    /**
     * Called from the CORBA Interceptors on the server-side when the server is replying to the client (local + remote
     * client). Check if there is an active transaction and remove it from TLS.
     */
    @Override
    public void checkTransactionImport() {
        // First check if this is a local call
        int[] count = threadLocalCallCounterHolder.get();
        if (count != null && count[0] > 0) {
            count[0]--;
            return;
        }

        // A remote call, clear TLS so that if this thread is reused
        // later, the current tx doesnt hang around.
        clearThreadTx();
    }

    /**
     * Called from the CORBA Interceptors on the client-side when a client makes a call to a remote object (not in the same
     * JVM). Check if there is an active, exportable transaction.
     *
     * @exception RuntimeException if the transaction is not exportable
     */
    @Override
    public void checkTransactionExport(boolean isLocal) {
        if (isLocal) {
            // Put a counter in TLS indicating this is a local call.
            // Use int[1] as a mutable java.lang.Integer!
            int[] count = threadLocalCallCounterHolder.get();
            if (count == null) {
                count = new int[1];
                threadLocalCallCounterHolder.set(count);
            }
            count[0]++;
            return;
        }

        JavaEETransaction eeTransaction = threadLocalTransactionHolder.get();
        if (eeTransaction == null || !eeTransaction.isLocalTx()) { // a JTS tx, can be exported
        	return;
        }

        // Check if a local tx with non-XA resource is being exported.
        // XXX what if this is a call on a non-transactional remote object ?
        if (eeTransaction.getNonXAResource() != null) {
            throw new RuntimeException("enterprise_distributedtx.cannot_export_transaction_having_nonxa");
        }

        // If we came here, it means we have a local tx with no registered
        // resources, so start a JTS tx which can be exported.
        try {
            startJTSTx(eeTransaction);
        } catch (Exception e) {
            throw new RuntimeException("enterprise_distributedtx.unable_tostart_JTSTransaction", e);
        }
    }

    /**
     * This is used by importing transactions via the Connector contract. Should not be called
     *
     * @return a <code>XATerminator</code> instance.
     * @throws UnsupportedOperationException
     */
    @Override
    public XATerminator getXATerminator() {
        return getDelegate().getXATerminator();
    }

    /**
     * Release a transaction. This call causes the calling thread to be dissociated from the specified transaction.
     * <p>
     * This is used by importing transactions via the Connector contract.
     *
     * @param xid the Xid object representing a transaction.
     */
    @Override
    public void release(Xid xid) throws WorkException {
        getDelegate().release(xid);
    }

    /**
     * Recreate a transaction based on the Xid. This call causes the calling thread to be associated with the specified
     * transaction.
     * <p>
     * This is used by importing transactions via the Connector contract.
     *
     * @param xid the Xid object representing a transaction.
     */
    @Override
    public void recreate(Xid xid, long timeout) throws WorkException {
        getDelegate().recreate(xid, timeout);
    }



    /****************************************************************************/
    /** Implementations of JTA TransactionManager APIs **************************/
    /****************************************************************************/

    @Override
    public void registerSynchronization(Synchronization sync) throws IllegalStateException, SystemException {
        _logger.log(FINE, "TM: registerSynchronization");

        try {
            Transaction transaction = getTransaction();
            if (transaction != null) {
                transaction.registerSynchronization(sync);
            }
        } catch (RollbackException ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.rollbackexcep_in_regsynch", ex);
            throw new IllegalStateException();
        }
    }

    // Implementation of begin() is moved to begin(int timeout)
    @Override
    public void begin() throws NotSupportedException, SystemException {
        begin(getEffectiveTimeout());
    }

    /**
     * This method is introduced as part of implementing the local transaction timeout capability. Implementation of begin()
     * moved here. Previpusly there is no timeout infrastructure for local txns, so when ever a timeout required for local
     * txn, it uses the globaltxn timeout infrastructure by doing an XA simulation.
     **/
    @Override
    public void begin(int timeout) throws NotSupportedException, SystemException {
        // Check if EE Transaction already exists
        if (threadLocalTransactionHolder.get() != null) {
            throw new NotSupportedException("enterprise_distributedtx.notsupported_nested_transaction");
        }

        setDelegate();

        // Check if JTS tx exists, without starting JTS tx.
        // This is needed in case the JTS tx was imported from a client.
        if (getStatus() != STATUS_NO_TRANSACTION) {
            throw new NotSupportedException("enterprise_distributedtx.notsupported_nested_transaction");
        }

        if (monitoringEnabled) {
            getDelegate().getReadLock().lock(); // XXX acquireReadLock();
            try {
                JavaEETransactionImpl eeTransaction = initJavaEETransaction(timeout);
                activeTransactions.add(eeTransaction);
                // TODO
                // monitor.transactionActivatedEvent();
                ComponentInvocation currentInvocation = invocationManager.getCurrentInvocation();
                if (currentInvocation != null && currentInvocation.getInstance() != null) {
                    eeTransaction.setComponentName(currentInvocation.getInstance().getClass().getName());
                }
            } finally {
                getDelegate().getReadLock().unlock(); // XXX releaseReadLock();
            }
        } else {
            initJavaEETransaction(timeout);
        }
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        boolean acquiredlock = false;

        try {
            JavaEETransaction eeTransaction = threadLocalTransactionHolder.get();
            if (eeTransaction != null && eeTransaction.isLocalTx()) {
                if (monitoringEnabled) {
                    getDelegate().getReadLock().lock(); // XXX acquireReadLock();
                    acquiredlock = true;
                }
                eeTransaction.commit(); // commit local tx
            } else {
                try {
                    // an XA transaction
                    getDelegate().commitDistributedTransaction();
                } finally {
                    if (eeTransaction != null) {
                        ((JavaEETransactionImpl) eeTransaction).onTxCompletion(true);
                    }
                }
            }

        } finally {
            setCurrentTransaction(null); // clear current thread's tx
            threadLocalDelegateHolder.set(null);
            if (acquiredlock) {
                getDelegate().getReadLock().unlock(); // XXX releaseReadLock();
            }
        }
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        boolean acquiredlock = false;
        try {
            JavaEETransaction eeTransaction = threadLocalTransactionHolder.get();
            if (eeTransaction != null && eeTransaction.isLocalTx()) {
                if (monitoringEnabled) {
                    getDelegate().getReadLock().lock(); // XXX acquireReadLock();
                    acquiredlock = true;
                }
                eeTransaction.rollback(); // rollback local tx
            } else {
                try {
                    // an XA transaction
                    getDelegate().rollbackDistributedTransaction();
                } finally {
                    if (eeTransaction != null) {
                        ((JavaEETransactionImpl) eeTransaction).onTxCompletion(false);
                    }
                }
            }

        } finally {
            setCurrentTransaction(null); // clear current thread's tx
            threadLocalDelegateHolder.set(null);
            if (acquiredlock) {
                getDelegate().getReadLock().unlock(); // XXX releaseReadLock();
            }
        }
    }

    @Override
    public int getStatus() throws SystemException {
        return getDelegate().getStatus();
    }

    @Override
    public Transaction getTransaction() throws SystemException {
        return getDelegate().getTransaction();
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        JavaEETransaction eeTransaction = threadLocalTransactionHolder.get();
        if (eeTransaction != null && eeTransaction.isLocalTx()) {
            if (monitoringEnabled) {
                getDelegate().getReadLock().lock(); // XXX acquireReadLock();
                try {
                    eeTransaction.setRollbackOnly();
                } finally {
                    getDelegate().getReadLock().unlock(); // XXX releaseReadLock();
                }
            } else {
                eeTransaction.setRollbackOnly();
            }
        }
        else {
            getDelegate().setRollbackOnlyDistributedTransaction(); // probably a JTS imported tx
        }
    }

    @Override
    public Transaction suspend() throws SystemException {
        return getDelegate().suspend(threadLocalTransactionHolder.get());
    }

    @Override
    public void resume(Transaction transaction) throws InvalidTransactionException, IllegalStateException, SystemException {
        if (threadLocalTransactionHolder.get() != null) {
            throw new IllegalStateException("enterprise_distributedtx.transaction_exist_on_currentThread");
        }

        if (transaction != null) {
            int status = transaction.getStatus();
            if (status == STATUS_ROLLEDBACK || status == STATUS_COMMITTED || status == STATUS_NO_TRANSACTION || status == STATUS_UNKNOWN) {
                throw new InvalidTransactionException("enterprise_distributedtx.resume_invalid_transaction");
            }
        } else {
            throw new InvalidTransactionException("enterprise_distributedtx.resume_invalid_transaction");
        }

        if (transaction instanceof JavaEETransactionImpl) {
            JavaEETransactionImpl eeTransaction = (JavaEETransactionImpl) transaction;
            if (!eeTransaction.isLocalTx()) {
                getDelegate().resume(eeTransaction.getJTSTx());
            }

            setCurrentTransaction(eeTransaction);
        } else {
            getDelegate().resume(transaction); // probably a JTS imported tx
        }
    }

    /**
     * Modify the value of the timeout value that is associated with the transactions started by the current thread with the
     * begin method.
     *
     * <p>
     * If an application has not called this method, the transaction service uses some default value for the transaction
     * timeout.
     *
     * @exception SystemException Thrown if the transaction manager encounters an unexpected error condition
     *
     */
    @Override
    public void setTransactionTimeout(int seconds) throws SystemException {
        if (seconds < 0) {
            throw new SystemException("enterprise_distributedtx.invalid_timeout");
        }

        txnTmout.set(seconds);
    }

    /**
     * Modify the value to be used to purge transaction tasks after the specified number of cancelled tasks.
     */
    @Override
    public void setPurgeCancelledTtransactionsAfter(int num) {
        purgeCancelledTtransactions = num;
    }

    /**
     * Returns the value to be used to purge transaction tasks after the specified number of cancelled tasks.
     */
    @Override
    public int getPurgeCancelledTtransactionsAfter() {
        return purgeCancelledTtransactions;
    }

    @Override
    public JavaEETransaction getCurrentTransaction() {
        return threadLocalTransactionHolder.get();
    }

    @Override
    public void setCurrentTransaction(JavaEETransaction eeTransaction) {
        threadLocalTransactionHolder.set(eeTransaction);
    }

    @Override
    public XAResourceWrapper getXAResourceWrapper(String className) {
        return getDelegate().getXAResourceWrapper(className);
    }

    @Override
    public void handlePropertyUpdate(String name, Object value) {
        instanceDelegate.handlePropertyUpdate(name, value);
        // XXX Check if the current delegate needs to be called as well.
    }

    @Override
    public boolean recoverIncompleteTx(boolean delegated, String logPath, XAResource[] xaresArray) throws Exception {
        return instanceDelegate.recoverIncompleteTx(delegated, logPath, xaresArray);
    }



    /****************************************************************************/
    /*********************** Called by Admin Framework **************************/
    /****************************************************************************/
    /*
     * Called by Admin Framework to freeze the transactions.
     */
    @Override
    public synchronized void freeze() {
        getDelegate().acquireWriteLock();
        // monitor.freezeEvent(true);
    }

    /*
     * Called by Admin Framework to freeze the transactions. These undoes the work done by the freeze.
     */
    @Override
    public synchronized void unfreeze() {
        getDelegate().releaseWriteLock();
        // monitor.freezeEvent(false);
    }

    /**
     * XXX ???
     */
    @Override
    public boolean isFrozen() {
        return getDelegate().isWriteLocked();
    }

    @Override
    public void cleanTxnTimeout() {
        txnTmout.set(null);
    }

    public int getEffectiveTimeout() {
        Integer tmout = txnTmout.get();
        if (tmout == null) {
            return transactionTimeout;
        }

        return tmout;
    }

    @Override
    public void setDefaultTransactionTimeout(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }

        transactionTimeout = seconds;
    }

    /*
     * This method returns the details of the Currently Active Transactions Called by Admin Framework when transaction
     * monitoring is enabled
     *
     * @return ArrayList of TransactionAdminBean
     *
     * @see TransactionAdminBean
     */
    @Override
    public List<TransactionAdminBean> getActiveTransactions() {
        List<TransactionAdminBean> transactionBeans = new ArrayList<>();
        txnTable = new Hashtable<>();

        List<Transaction> activeCopy = new ArrayList<>(activeTransactions); // get the clone of the active transactions
        for (Transaction transaction : activeCopy) {
            try {
                TransactionAdminBean transactionBean = getDelegate().getTransactionAdminBean(transaction);
                if (transactionBean == null) {
                    // Shouldn't happen
                    _logger.warning("enterprise_distributedtx.txbean_null" + transaction);
                } else {
                    if (_logger.isLoggable(FINE)) {
                        _logger.log(FINE, "TM: Adding txnId " + transactionBean.getId() + " to txnTable");
                    }

                    txnTable.put(transactionBean.getId(), transaction);
                    transactionBeans.add(transactionBean);
                }
            } catch (Exception ex) {
                _logger.log(SEVERE, "transaction.monitor.error_while_getting_monitor_attr", ex);
            }
        }

        return transactionBeans;
    }

    public TransactionAdminBean getTransactionAdminBean(Transaction transaction) throws SystemException {
        TransactionAdminBean transactionBean = null;

        if (transaction instanceof JavaEETransaction) {
            JavaEETransactionImpl eeTransaction = (JavaEETransactionImpl) transaction;

            transactionBean = new TransactionAdminBean(
                    transaction,
                    eeTransaction.getTransactionId(),
                    getStatusAsString(transaction.getStatus()),
                    System.currentTimeMillis() - eeTransaction.getStartTime(),
                    eeTransaction.getComponentName(),
                    eeTransaction.getResourceNames());
        }

        return transactionBean;
    }

    /*
     * Called by Admin Framework when transaction monitoring is enabled
     */
    @Override
    public void forceRollback(String txnId) throws IllegalStateException, SystemException {
        if (txnTable == null || txnTable.size() == 0) {
            getActiveTransactions();
        }

        if (txnTable == null || txnTable.get(txnId) == null) {
            throw new IllegalStateException("transaction.monitor.rollback_invalid_id");
        }

        _logger.log(FINE, () -> "TM: Marking txnId " + txnId + " for rollback");

        txnTable.get(txnId).setRollbackOnly();
    }

    @Override
    public void setMonitoringEnabled(boolean enabled) {
        monitoringEnabled = enabled;
        // reset the variables
        activeTransactions.clear();
    }

    private void _monitorTxCompleted(Object obj, boolean committed) {
        if (obj != null) {
            if (obj instanceof JavaEETransactionImpl) {
                JavaEETransactionImpl t = (JavaEETransactionImpl) obj;
                if (!t.isLocalTx()) {
                    obj = t.getJTSTx();
                }
            }
            if (activeTransactions.remove(obj)) {
                if (committed) {
                    // TODO
                    // monitor.transactionCommittedEvent();
                } else {
                    // monitor.transactionRolledbackEvent();
                }
            } else {
                // WARN ???
            }
        }
    }

    // Mods: Adding method for statistic dumps using TimerTask
    private void registerStatisticMonitorTask() {
        TimerTask task = new StatisticMonitorTask();

        // For now, get monitoring interval from system prop
        int statInterval = 2 * 60 * 1000;
        try {
            String interval = System.getProperty("MONITOR_JTA_RESOURCE_TABLE_SECONDS");
            int temp = Integer.parseInt(interval);
            if (temp > 0) {
                statInterval = temp;
            }
        } catch (Exception ex) {
            // ignore
        }

        _timer.scheduleAtFixedRate(task, 0, statInterval);
    }

    // Mods: Adding TimerTask class for statistic dumps
    class StatisticMonitorTask extends TimerTask {
        @Override
        public void run() {
            if (resourceTable != null) {
                Map stats = resourceTable.getStats();
                Iterator it = stats.entrySet().iterator();
                _logger.log(Level.INFO, "********** JavaEETransactionManager resourceTable stats *****");
                while (it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    _logger.log(Level.INFO, (String) entry.getKey() + ": " + entry.getValue());
                }
            }
        }
    }



    /****************************************************************************/
    /************************* Helper Methods ***********************************/
    /****************************************************************************/

    public static String getStatusAsString(int status) {
        return statusMap.get(status);
    }

    private void delistComponentResources(ComponentInvocation componentInvocation, boolean suspend) throws InvocationException {
        try {
            Transaction transaction = (Transaction) componentInvocation.getTransaction();
            if (isTransactionActive(transaction)) {
                List<TransactionalResource> existingResourceList = getExistingResourceList(componentInvocation.getInstance(), componentInvocation);
                if (existingResourceList == null || existingResourceList.size() == 0) {
                    return;
                }

                int flag = suspend ? XAResource.TMSUSPEND : XAResource.TMSUCCESS;
                Iterator<TransactionalResource> resourceListIterator = existingResourceList.iterator();
                while (resourceListIterator.hasNext()) {
                    TransactionalResource transactionalResource = resourceListIterator.next();
                    try {
                        if (transactionalResource.isEnlisted()) {
                            delistResource(transaction, transactionalResource, flag);
                        }
                    } catch (IllegalStateException ex) {
                        _logger.log(FINE, ex, () ->  "TM: Exception in delistResource");
                        // ignore error due to tx time out
                    } catch (Exception ex) {
                        _logger.log(FINE, ex, () -> "TM: Exception in delistResource");
                        resourceListIterator.remove();
                        handleResourceError(transactionalResource, ex, transaction);
                    }
                }
            }
        } catch (Exception ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_delist", ex);
        }
    }

    protected boolean enlistXAResource(Transaction transaction, TransactionalResource transactionalResource) throws RollbackException, IllegalStateException, SystemException {
        _logger.log(FINE, () ->
            "\n\nIn JavaEETransactionManagerSimplified.enlistXAResource, h=" + transactionalResource +
            " h.xares=" + transactionalResource.getXAResource() +
            " tran=" + transaction);

        if (!resourceEnlistable(transactionalResource)) {
            return true;
        }

        _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.enlistXAResource - enlistable");

        boolean result = transaction.enlistResource(transactionalResource.getXAResource());

        if (!transactionalResource.isEnlisted()) {
            _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.enlistXAResource - enlist");
        }

        transactionalResource.enlistedInTransaction(transaction);

        return result;
    }

    private void enlistComponentResources(ComponentInvocation componentInvocation) throws InvocationException {
        try {
            Transaction transaction = (Transaction) componentInvocation.getTransaction();
            if (isTransactionActive(transaction)) {
                List<TransactionalResource> existingResourceList = getExistingResourceList(componentInvocation.getInstance(), componentInvocation);
                if (existingResourceList == null || existingResourceList.size() == 0) {
                    return;
                }

                Iterator<TransactionalResource> resourceListIterator = existingResourceList.iterator();
                while (resourceListIterator.hasNext()) {
                    TransactionalResource transactionalResource = resourceListIterator.next();
                    try {
                        enlistResource(transaction, transactionalResource);
                    } catch (Exception ex) {
                        _logger.log(FINE, "enterprise_distributedtx.pooling_excep", ex);

                        resourceListIterator.remove();
                        handleResourceError(transactionalResource, ex, transaction);
                    }
                }
            }
        } catch (Exception ex) {
            _logger.log(SEVERE, "enterprise_distributedtx.excep_in_enlist", ex);
        }
    }

    /**
     * Called by #componentDestroyed()
     */
    private void processResourceList(List<TransactionalResource> resourceList) {
        if (resourceList != null && resourceList.size() > 0) {
            Iterator<TransactionalResource> resourceListIterator = resourceList.iterator();
            while (resourceListIterator.hasNext()) {
                TransactionalResource transactionalResource = resourceListIterator.next();
                try {
                    transactionalResource.closeUserConnection();
                } catch (Exception ex) {
                    _logger.log(Level.FINE, "enterprise_distributedtx.pooling_excep", ex);
                }
            }

            resourceList.clear();
        }
    }

    private void handleResourceError(TransactionalResource transactionalResource, Exception ex, Transaction transaction) {
        if (_logger.isLoggable(FINE)) {
            if (transactionalResource.isTransactional()) {
                _logger.log(FINE, "TM: HandleResourceError " + transactionalResource.getXAResource() + ", " + ex);
            }
        }

        try {
            if (transaction != null && transactionalResource.isTransactional() && transactionalResource.isEnlisted()) {
                transaction.delistResource(transactionalResource.getXAResource(), XAResource.TMSUCCESS);
            }
        } catch (Exception ex2) {
            // ignore
        }

        if (ex instanceof RollbackException) {
            // transaction marked as rollback
            return;
        }

        if (ex instanceof IllegalStateException) {
            // Transaction aborted by time out
            // close resource
            try {
                transactionalResource.closeUserConnection();
            } catch (Exception ex2) {
                // Log.err.println(ex2);
            }
        } else {
            // destroy resource. RM Error.
            try {
                transactionalResource.destroyResource();
            } catch (Exception ex2) {
                // Log.err.println(ex2);
            }
        }
    }

    private Object getResourceTableKey(Object instance, ComponentInvocation componentInvocation) {
        Object key = null;
        if (componentInvocation != null) {
            key = componentInvocation.getResourceTableKey();
        }

        // If ComponentInvocation is null or doesn't hold the key, use instance as the key.
        if (key == null) {
            key = instance;
        }

        return key;
    }

    private boolean isTransactionActive(Transaction transaction) {
        return transaction != null;
    }

    /**
     * JTS version of the #delistResource
     *
     * @param suspend true if the transaction association should be suspended rather than ended.
     */
    private boolean delistJTSResource(Transaction transaction, TransactionalResource transactionalResource, int flag) throws IllegalStateException, SystemException {
        // ** XXX Throw an exception instead ??? XXX **
        if (_logger.isLoggable(FINE)) {
            _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.delistJTSResource, h=" + transactionalResource + " h.xares=" + transactionalResource.getXAResource()
                    + " tran=" + transaction + " flag=" + flag);
        }

        if (!transactionalResource.isShareable() || multipleEnlistDelists) {
             _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.delistJTSResource - !h.isShareable() || multipleEnlistDelists");

            if (transactionalResource.isTransactional() && transactionalResource.isEnlisted()) {
                _logger.log(FINE, "\n\nIn JavaEETransactionManagerSimplified.delistJTSResource - delist");

                return transaction.delistResource(transactionalResource.getXAResource(), flag);
            }
        }

        return true;
    }

    private void remove(Transaction tx) {
        getDelegate().removeTransaction(tx);

        /**
         * XXX TO BE MOVED TO XA DELEGATE XXX javaEETM.globalTransactions.remove(jtsTx); XXX TO BE MOVED TO XA DELEGATE XXX
         **/
    }

    /**
     * Called by JavaEETransactionImpl also
     */
    synchronized JavaEETransactionManagerDelegate getDelegate() {
        JavaEETransactionManagerDelegate threadLocalDelegate = threadLocalDelegateHolder.get();
        return threadLocalDelegate != null ? threadLocalDelegate : instanceDelegate;
    }

    private JavaEETransactionManagerDelegate setDelegate() {
        JavaEETransactionManagerDelegate threadLocalDelegate = threadLocalDelegateHolder.get();
        if (threadLocalDelegate == null) {
            threadLocalDelegate = instanceDelegate;
            threadLocalDelegateHolder.set(threadLocalDelegate);
        }

        return threadLocalDelegate;
    }

    public boolean isDelegate(JavaEETransactionManagerDelegate testDelegate) {
        if (instanceDelegate == null) {
            return false;
        }

        return testDelegate.getClass().getName().equals(instanceDelegate.getClass().getName());
    }

    private void initDelegates() {
        if (serviceLocator == null) {
            return; // the delegate will be set explicitly
        }

        for (JavaEETransactionManagerDelegate delegate : serviceLocator.getAllServices(JavaEETransactionManagerDelegate.class)) {
            setDelegate(delegate);
        }

        if (instanceDelegate != null && _logger.isLoggable(FINE)) {
            _logger.log(Level.INFO, "enterprise_used_delegate_name", instanceDelegate.getClass().getName());
        }

    }

    @Override
    public synchronized void setDelegate(JavaEETransactionManagerDelegate newDelegate) {
        // XXX Check if it's valid to set or if we need to remember all that asked.

        int currentdelegateOrder = 0;
        if (instanceDelegate != null) {
            currentdelegateOrder = instanceDelegate.getOrder();
        }

        if (newDelegate.getOrder() > currentdelegateOrder) {
            instanceDelegate = newDelegate;

            // XXX Hk2 work around XXX
            instanceDelegate.setTransactionManager(this);

            if (_logger.isLoggable(FINE)) {
                _logger.log(FINE, "Replaced delegate with " + newDelegate.getClass().getName());
            }
        }
    }

    public Logger getLogger() {
        return _logger;
    }

    public void monitorTxCompleted(Object obj, boolean b) {
        if (monitoringEnabled) {
            _monitorTxCompleted(obj, b);
        }
    }

    public void monitorTxBegin(Transaction tx) {
        if (monitoringEnabled) {
            activeTransactions.add(tx);
            // TODO
            // monitor.transactionActivatedEvent();
        }
    }

    public boolean resourceEnlistable(TransactionalResource h) {
        return (h.isTransactional() && (!h.isEnlisted() || !h.isShareable() || multipleEnlistDelists));
    }

    public boolean isInvocationStackEmpty() {
        return (invocationManager == null || invocationManager.isInvocationStackEmpty());
    }

    public void setTransactionCompeting(boolean b) {
        ComponentInvocation curr = invocationManager.getCurrentInvocation();
        if (curr != null) {
            curr.setTransactionCompeting(b);
        }
    }

    public JavaEETransaction createImportedTransaction(TransactionInternal jtsTx) throws SystemException {
        JavaEETransactionImpl eeTransaction = new JavaEETransactionImpl(jtsTx, this);
        try {
            jtsTx.registerSynchronization(new JTSSynchronization(jtsTx, this));
        } catch (Exception ex) {
            throw new SystemException(ex.toString());
        }

        return eeTransaction;
    }


    /****************************************************************************/
    /** Implementation of jakarta.transaction.Synchronization *********************/
    /****************************************************************************/

    private static class JTSSynchronization implements Synchronization {
        private TransactionInternal jtsTx;
        private JavaEETransactionManagerImpl javaEETM;

        JTSSynchronization(TransactionInternal jtsTx, JavaEETransactionManagerImpl javaEETM) {
            this.jtsTx = jtsTx;
            this.javaEETM = javaEETM;
        }

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            javaEETM.remove(jtsTx);
        }
    }
}
