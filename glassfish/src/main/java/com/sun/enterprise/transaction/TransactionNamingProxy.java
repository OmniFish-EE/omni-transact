/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.enterprise.transaction;

import javax.naming.NamingException;

import org.glassfish.api.admin.ProcessEnvironment;
import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.api.naming.NamedNamingObjectProxy;
import org.glassfish.api.naming.NamespacePrefixes;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Service;

import com.sun.enterprise.transaction.spi.TransactionOperationsManager;

import jakarta.inject.Inject;

/**
 * Proxy for creating JTA instances that get registered in the naming manager. NamingManager will call the handle()
 * method when the JNDI name is looked up. Will return the instance that corresponds to the known name.
 *
 * @author Marina Vatkina
 */
@Service
@NamespacePrefixes({
    TransactionNamingProxy.USER_TX,
    TransactionNamingProxy.TRANSACTION_SYNC_REGISTRY,
    TransactionNamingProxy.APPSERVER_TRANSACTION_MGR,
    TransactionNamingProxy.APPSERVER_TRANSACTION_SYNC_REGISTRY })
public class TransactionNamingProxy implements NamedNamingObjectProxy {

    @Inject
    private ServiceLocator habitat;

    @Inject
    private ProcessEnvironment processEnv; // Here for ordering

    static final String USER_TX = "java:comp/UserTransaction";
    static final String USER_TX_NO_JAVA_COMP = "UserTransaction";

    static final String TRANSACTION_SYNC_REGISTRY = "java:comp/TransactionSynchronizationRegistry";
    static final String APPSERVER_TRANSACTION_SYNC_REGISTRY = "java:appserver/TransactionSynchronizationRegistry";

    static final String APPSERVER_TRANSACTION_MGR = "java:appserver/TransactionManager";

    @Override
    public Object handle(String name) throws NamingException {
        if (USER_TX.equals(name)) {
            checkUserTransactionLookupAllowed();
            return habitat.getService(UserTransactionImpl.class);
        }

        if (TRANSACTION_SYNC_REGISTRY.equals(name) || APPSERVER_TRANSACTION_SYNC_REGISTRY.equals(name)) {
            return habitat.getService(TransactionSynchronizationRegistryImpl.class);
        }

        if (APPSERVER_TRANSACTION_MGR.equals(name)) {
            return habitat.getService(TransactionManagerHelper.class);
        }

        return null;
    }

    private void checkUserTransactionLookupAllowed() throws NamingException {
        InvocationManager invocationManager = habitat.getService(InvocationManager.class);
        if (invocationManager != null) {
            ComponentInvocation inv = invocationManager.getCurrentInvocation();
            if (inv != null) {
                TransactionOperationsManager toMgr = (TransactionOperationsManager) inv.getTransactionOperationsManager();
                if (toMgr != null) {
                    toMgr.userTransactionLookupAllowed();
                }
            }
        }
    }
}
