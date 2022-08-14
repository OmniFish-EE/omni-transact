/*
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

package com.sun.jts.codegen.otsidl;

/**
 * com/sun/jts/codegen/otsidl/JCoordinatorOperations.java . Generated by the IDL-to-Java compiler (portable), version
 * "3.1" from com/sun/jts/ots.idl Tuesday, February 5, 2002 12:57:23 PM PST
 */

//#-----------------------------------------------------------------------------
public interface JCoordinatorOperations extends org.omg.CosTransactions.CoordinatorOperations {
    org.omg.CosTransactions.otid_t getGlobalTID();

    // Returns the global identifier that represents the Coordinator's transaction.
    long getLocalTID();

    // Returns the local identifier that represents the Coordinator's transaction.
    org.omg.CosTransactions.TransIdentity[] getAncestors();

    // freeing the sequence storage.
    boolean isRollbackOnly();
} // interface JCoordinatorOperations
