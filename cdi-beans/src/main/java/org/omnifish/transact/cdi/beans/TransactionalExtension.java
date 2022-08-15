/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.omnifish.transact.cdi.beans;

import org.omnifish.transact.api.impl.InvocationManagerImpl;
import org.omnifish.transact.api.impl.TransactionServiceConfigImpl;
import org.omnifish.transact.jta.transaction.JavaEETransactionManagerImpl;
import org.omnifish.transact.jta.transaction.JavaEETransactionManagerSimplifiedDelegate;
import org.omnifish.transact.jta.transaction.TransactionManagerImpl;
import org.omnifish.transact.jta.transaction.TransactionSynchronizationRegistryImpl;
import org.omnifish.transact.jta.transaction.UserTransactionImpl;
import org.omnifish.transact.jts.JavaEETransactionManagerJTSDelegate;
import org.omnifish.transact.jts.ResourceRecoveryManagerImpl;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

/**
 * The CDI Portable Extension for @Transactional.
 */
public class TransactionalExtension implements Extension {

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscoveryEvent, BeanManager beanManager) {
        addAnnotatedTypes(beforeBeanDiscoveryEvent, beanManager,
            CDIServiceLocator.class,

            // Jakarta Transactions
            UserTransactionImpl.class,
            TransactionManagerImpl.class,
            JavaEETransactionManagerImpl.class,
            TransactionSynchronizationRegistryImpl.class,
            JavaEETransactionManagerSimplifiedDelegate.class,

            // Java Transaction Service
            JavaEETransactionManagerJTSDelegate.class,
            ResourceRecoveryManagerImpl.class,

            // Transact API default implementations
            TransactionServiceConfigImpl.class,
            InvocationManagerImpl.class
        );

    }

    public static void addAnnotatedTypes(BeforeBeanDiscovery beforeBean, BeanManager beanManager, Class<?>... types) {
        for (Class<?> type : types) {
            beforeBean.addAnnotatedType(beanManager.createAnnotatedType(type), "GlassFish-TX-" + type.getName());
        }
    }

}
