/*
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

package org.omnifish.transact.api.api;

import java.util.Set;

import org.omnifish.transact.api.spi.TransactionalResource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transaction;

public interface JavaEETransaction extends Transaction {

    SimpleResource getExtendedEntityManagerResource(EntityManagerFactory factory);

    SimpleResource getTxEntityManagerResource(EntityManagerFactory factory);

    void addTxEntityManagerMapping(EntityManagerFactory factory, SimpleResource em);

    void addExtendedEntityManagerMapping(EntityManagerFactory factory, SimpleResource em);

    void removeExtendedEntityManagerMapping(EntityManagerFactory factory);

    <T> void setContainerData(T data);

    <T> T getContainerData();

    Set getAllParticipatingPools();

    Set getResources(Object poolInfo);

    TransactionalResource getLAOResource();

    void setLAOResource(TransactionalResource h);

    TransactionalResource getNonXAResource();

    void setResources(Set resources, Object poolInfo);

    boolean isLocalTx();

    boolean isTimedOut();
}
