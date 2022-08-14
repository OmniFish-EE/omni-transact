/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.transaction.startup;

import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;

import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.EventTypes;
import org.glassfish.api.event.Events;
import org.glassfish.api.naming.GlassfishNamingManager;
import org.glassfish.api.naming.NamingObjectProxy;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;

import com.sun.enterprise.transaction.api.JavaEETransactionManager;
import com.sun.enterprise.transaction.config.TransactionService;
import com.sun.logging.LogDomains;

import jakarta.inject.Inject;

/**
 * Service wrapper to only lookup the transaction recovery when there
 * are applications deployed since the actual service has ORB dependency.
 *
 * This is also responsible for binding (non java:comp) UserTransaction in naming tree.
 */
@Service
@RunLevel( value=10 )
public class TransactionLifecycleService implements PostConstruct, PreDestroy {

    @Inject
    ServiceLocator serviceLocator;

    @Inject
    Events events;

    @Inject @Optional
    GlassfishNamingManager glassfishNamingManager;

    static final String USER_TX_NO_JAVA_COMP = "UserTransaction";

    private static Logger _logger = LogDomains.getLogger(TransactionLifecycleService.class, LogDomains.JTA_LOGGER);

    private JavaEETransactionManager javaEETransactionManager;

    @Override
    public void postConstruct() {
        EventListener glassfishEventListener = new EventListener() {
            @Override
            public void event(Event event) {
                if (event.is(EventTypes.SERVER_READY)) {
                    _logger.fine("TM LIFECYCLE SERVICE - ON READY");
                    onReady();
                } else if (event.is(EventTypes.PREPARE_SHUTDOWN)) {
                    _logger.fine("TM LIFECYCLE SERVICE - ON SHUTDOWN");
                    onShutdown();
                }
            }
        };

        events.register(glassfishEventListener);

        if (glassfishNamingManager != null) {
            try {
                glassfishNamingManager.publishObject(USER_TX_NO_JAVA_COMP, new NamingObjectProxy.InitializationNamingObjectProxy() {
                    @Override
                    public Object create(Context ic) throws NamingException {
                        ActiveDescriptor<?> descriptor = serviceLocator.getBestDescriptor(
                                BuilderHelper.createContractFilter("jakarta.transaction.UserTransaction"));

                        if (descriptor == null) {
                            return null;
                        }

                        return serviceLocator.getServiceHandle(descriptor).getService();
                    }
                }, false);
            } catch (NamingException e) {
                _logger.warning("Can't bind \"UserTransaction\" in JNDI");
            }
        }
    }

    @Override
    public void preDestroy() {
        if (glassfishNamingManager != null) {
            try {
                glassfishNamingManager.unpublishObject(USER_TX_NO_JAVA_COMP);
            } catch (NamingException e) {
                _logger.warning("Can't unbind \"UserTransaction\" in JNDI");
            }
        }
    }

    public void onReady() {
        _logger.fine("ON TM READY STARTED");

        TransactionService txnService = serviceLocator.getService(TransactionService.class);
        if (txnService != null) {
            if (Boolean.valueOf(txnService.getAutomaticRecovery())) {
                _logger.fine("ON TM RECOVERY START");

                javaEETransactionManager = serviceLocator.getService(JavaEETransactionManager.class);
                javaEETransactionManager.initRecovery(false);

                _logger.fine("ON TM RECOVERY END");
            }
        }

        _logger.fine("ON TM READY FINISHED");
    }

    public void onShutdown() {
        // Cleanup if TM was loaded
        if (javaEETransactionManager == null) {
            ServiceHandle<JavaEETransactionManager> serviceHandle = serviceLocator.getServiceHandle(JavaEETransactionManager.class);
            if (serviceHandle != null && serviceHandle.isActive()) {
                javaEETransactionManager = serviceHandle.getService();
            }
        }

        if (javaEETransactionManager != null) {
            _logger.fine("ON TM SHUTDOWN STARTED");

            javaEETransactionManager.shutdown();

            _logger.fine("ON TM SHUTDOWN FINISHED");
        }

    }

}
