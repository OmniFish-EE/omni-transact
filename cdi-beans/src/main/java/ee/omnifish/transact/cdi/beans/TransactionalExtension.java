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

package ee.omnifish.transact.cdi.beans;

import static ee.omnifish.transact.jta.cdi.TransactionScopedCDIUtil.createManaged;

import ee.omnifish.transact.api.InvocationManager;
import ee.omnifish.transact.api.JavaEETransactionManager;
import ee.omnifish.transact.api.ResourceRecoveryManager;
import ee.omnifish.transact.api.TransactionServiceConfig;
import ee.omnifish.transact.api.impl.InvocationManagerImpl;
import ee.omnifish.transact.api.impl.TransactionServiceConfigImpl;
import ee.omnifish.transact.api.spi.JavaEETransactionManagerDelegate;
import ee.omnifish.transact.api.spi.ServiceLocator;
import ee.omnifish.transact.jta.transaction.JavaEETransactionManagerImpl;
import ee.omnifish.transact.jta.transaction.JavaEETransactionManagerSimplifiedDelegate;
import ee.omnifish.transact.jta.transaction.TransactionManagerImpl;
import ee.omnifish.transact.jta.transaction.TransactionSynchronizationRegistryImpl;
import ee.omnifish.transact.jta.transaction.UserTransactionImpl;
import ee.omnifish.transact.jts.JavaEETransactionManagerJTSDelegate;
import ee.omnifish.transact.jts.ResourceRecoveryManagerImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * The CDI Portable Extension for @Transactional.
 */
public class TransactionalExtension implements Extension {

    public void afterBean(final @Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .types(ServiceLocator.class, CDIServiceLocator.class)
                          .createWith(e -> new CDIServiceLocator());



        // Jakarta Transactions

        afterBeanDiscovery.addBean()
                           .scope(ApplicationScoped.class)
                           .name("java:comp/UserTransaction")
                           .types(UserTransaction.class, UserTransactionImpl.class)
                           .createWith(e -> createManaged(UserTransactionImpl.class, e));

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .name("java:comp/TransactionSynchronizationRegistry")
                          .types(TransactionSynchronizationRegistry.class)
                          .createWith(e -> createManaged(TransactionSynchronizationRegistryImpl.class, e));

        afterBeanDiscovery.addBean()
                           .scope(ApplicationScoped.class)
                           .name("java:appserver/TransactionManager")
                           .types(TransactionManager.class)
                           .createWith(e -> createManaged(TransactionManagerImpl.class, e));

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .types(Object.class, JavaEETransactionManagerDelegate.class, JavaEETransactionManagerSimplifiedDelegate.class)
                          .createWith(e -> createManaged(JavaEETransactionManagerSimplifiedDelegate.class, e));

        afterBeanDiscovery.addBean()
                           .scope(ApplicationScoped.class)
                           .types(JavaEETransactionManager.class)
                           .createWith(e -> createManaged(JavaEETransactionManagerImpl.class, e));



        // Java Transaction Service

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .types(Object.class, JavaEETransactionManagerDelegate.class, JavaEETransactionManagerJTSDelegate.class)
                          .createWith(e -> createManaged(JavaEETransactionManagerJTSDelegate.class, e));

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .types(ResourceRecoveryManager.class)
                          .createWith(e -> createManaged(ResourceRecoveryManagerImpl.class, e));



        // Transact API default implementations

        afterBeanDiscovery.addBean()
                          .scope(ApplicationScoped.class)
                          .types(TransactionServiceConfig.class)
                          .createWith(e -> createManaged(TransactionServiceConfigImpl.class, e));

        afterBeanDiscovery.addBean()
                          .scope(RequestScoped.class)
                          .types(InvocationManager.class)
                          .createWith(e -> createManaged(InvocationManagerImpl.class, e));

    }


    public static void addAnnotatedTypes(BeforeBeanDiscovery beforeBean, BeanManager beanManager, Class<?>... types) {
        for (Class<?> type : types) {
            beforeBean.addAnnotatedType(beanManager.createAnnotatedType(type), "GlassFish-TX-" + type.getName());
        }
    }

}
