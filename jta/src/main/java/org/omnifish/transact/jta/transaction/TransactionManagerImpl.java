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

import static org.omnifish.transact.api.ComponentInvocation.ComponentInvocationType.SERVLET_INVOCATION;

import java.rmi.RemoteException;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.omnifish.transact.api.ComponentInvocation;
import org.omnifish.transact.api.InvocationManager;
import org.omnifish.transact.api.JavaEETransactionManager;
import org.omnifish.transact.api.TransactionImport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.resource.spi.XATerminator;
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
 * This class is wrapper for the actual transaction manager implementation. JNDI lookup name
 * "java:appserver/TransactionManager" see the com/sun/enterprise/naming/java/javaURLContext.java
 **/
@ApplicationScoped
@Named("java:appserver/TransactionManager")
public class TransactionManagerImpl implements TransactionManager, TransactionImport {

    @Inject
    private JavaEETransactionManager transactionManager;

    @Inject
    private InvocationManager invocationManager;

    @Override
    public void begin() throws NotSupportedException, SystemException {
        transactionManager.begin();
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        transactionManager.commit();
    }

    @Override
    public int getStatus() throws SystemException {
        return transactionManager.getStatus();
    }

    @Override
    public Transaction getTransaction() throws SystemException {
        return transactionManager.getTransaction();
    }

    @Override
    public void resume(Transaction transaction) throws InvalidTransactionException, IllegalStateException, SystemException {
        transactionManager.resume(transaction);
        preInvokeTx(false);
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        transactionManager.rollback();
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        transactionManager.setRollbackOnly();
    }

    @Override
    public void setTransactionTimeout(int seconds) throws SystemException {
        transactionManager.setTransactionTimeout(seconds);
    }

    @Override
    public Transaction suspend() throws SystemException {
        postInvokeTx(true, false);
        return transactionManager.suspend();
    }

    @Override
    public void recreate(Xid xid, long timeout) {
        try {
            transactionManager.recreate(xid, timeout);
        } catch (WorkException ex) {
            throw new IllegalStateException(ex);
        }

        preInvokeTx(true);
    }

    @Override
    public void release(Xid xid) {
        postInvokeTx(false, true);

        try {
            transactionManager.release(xid);
        } catch (WorkException ex) {
            throw new IllegalStateException(ex);
        } finally {
            if (transactionManager instanceof JavaEETransactionManagerImpl) {
                ((JavaEETransactionManagerImpl) transactionManager).clearThreadTx();
            }
        }
    }

    @Override
    public XATerminator getXATerminator() {
        return transactionManager.getXATerminator();
    }

    @Override
    public int getTransactionRemainingTimeout() throws SystemException {
        int timeout = 0;
        Transaction transaction = getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("no current transaction");
        }

        if (transaction instanceof JavaEETransactionImpl) {
            timeout = ((JavaEETransactionImpl) transaction).getRemainingTimeout();
        }

        return timeout;
    }

    @Override
    public void registerRecoveryResourceHandler(XAResource xaResource) {
        transactionManager.registerRecoveryResourceHandler(xaResource);
    }

    /**
     * PreInvoke Transaction configuration for Servlet Container. BaseContainer.preInvokeTx() handles all this for CMT EJB.
     *
     * Compensate that JavaEEInstanceListener.handleBeforeEvent( BEFORE_SERVICE_EVENT) gets called before WSIT WSTX Service
     * pipe associates a JTA txn with incoming thread.
     *
     * Precondition: assumes JTA transaction already associated with current thread.
     */
    public void preInvokeTx(boolean checkServletInvocation) {
        final ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();

        if (componentInvocation != null && (!checkServletInvocation || componentInvocation.getInvocationType() == SERVLET_INVOCATION)) {
            try {
                // Required side effect: note that enlistComponentResources calls
                // ComponentInvocation.setTransaction(currentJTATxn).
                // If this is not correctly set, managed XAResource connections
                // are not auto enlisted when they are created.
                transactionManager.enlistComponentResources();
            } catch (RemoteException re) {
                throw new IllegalStateException(re);
            }
        }
    }

    /**
     * PostInvoke Transaction configuration for Servlet Container. BaseContainer.preInvokeTx() handles all this for CMT EJB.
     *
     * Precondition: assumed called prior to current transcation being suspended or released.
     *
     * @param suspend indicate whether the delisting is due to suspension or transaction completion(commmit/rollback)
     */
    public void postInvokeTx(boolean suspend, boolean checkServletInvocation) {
        final ComponentInvocation componentInvocation = invocationManager.getCurrentInvocation();
        if (componentInvocation != null && (!checkServletInvocation || componentInvocation.getInvocationType() == SERVLET_INVOCATION)) {
            try {
                transactionManager.delistComponentResources(suspend);
            } catch (RemoteException re) {
                throw new IllegalStateException(re);
            } finally {
                componentInvocation.setTransaction(null);
            }
        }
    }
}
