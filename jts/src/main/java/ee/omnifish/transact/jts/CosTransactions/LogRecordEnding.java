/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 1995-1997 IBM Corp. All rights reserved.
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

//----------------------------------------------------------------------------
//
// Module:      LogRecordEnding.java
//
// Description: Log record ending.
//
// Product:     ee.omnifish.transact.jts.CosTransactions
//
// Author:      Simon Holdsworth
//
// Date:        March, 1997
//----------------------------------------------------------------------------

package ee.omnifish.transact.jts.CosTransactions;

// Import required classes
import java.io.Serializable;

/**
 * A class containing ending information for a log record.
 *
 * @version 0.01
 *
 * @author Simon Holdsworth, IBM Corporation
 *
 * @see LogHandle
 */
//----------------------------------------------------------------------------
// CHANGE HISTORY
//
// Version By     Change Description
//   0.01  SAJH   Initial implementation.
//-----------------------------------------------------------------------------

class LogRecordEnding implements Serializable {
    /**
     * This constant holds the size of the LogRecordEnding object.
     */
    final static int SIZEOF = LogLSN.SIZEOF;

    /**
     * The log record ending contains the current LSN.
     */
    LogLSN currentLSN;

    /**
     * Default LogRecordEnding constructor.
     */
    LogRecordEnding() {
    }

    /**
     * Constructs a LogReocrdEnding from the given byte array.
     *
     * @param bytes The array of bytes from which the object is to be constructed.
     * @param index The index in the array where copy is to start.
     */
    LogRecordEnding(byte[] bytes, int index) {
        currentLSN = new LogLSN(bytes, index);
    }

    /**
     * Makes a byte representation of the LogRecordEnding.
     *
     * @param bytes The array of bytes into which the object is to be copied.
     * @param index The index in the array where copy is to start.
     *
     * @return Number of bytes copied.
     */
    final int toBytes(byte[] bytes, int index) {
        currentLSN.toBytes(bytes, index);

        return SIZEOF;
    }

    /**
     * This method is called to direct the object to format its state to a String.
     *
     * @return The formatted representation of the object.
     */
    @Override
    public final String toString() {
        return "LRE(curr="/* #Frozen */ + currentLSN + ")"/* #Frozen */;
    }
}
