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
// Module:      LogicErrorException.java
//
// Description: Internal logic error exception.
//
// Product:     ee.omnifish.transact.jts.CosTransactions
//
// Author:      Simon Holdsworth
//
// Date:        March, 1997
//----------------------------------------------------------------------------

package ee.omnifish.transact.jts.CosTransactions;

/**
 * This class provides an exception which can be thrown to indicate that some sort of logic error has occurred.
 *
 * @version 0.01
 *
 * @author Simon Holdsworth, IBM Corporation
 *
 */

//----------------------------------------------------------------------------
// CHANGE HISTORY
//
// Version By     Change Description
//   0.01  SAJH   Initial implementation.
//----------------------------------------------------------------------------

class LogicErrorException extends Exception {

    String reason = "";

    /**
     * Constructs the LogicErrorException with a reason string.
     *
     * @param reason The reason identifier.
     *
     * @return
     *
     * @see
     */
    LogicErrorException(String reason) {
    }

    /**
     * Converts the LogicErrorException to a string.
     *
     * @return The string representation.
     */
    @Override
    public String toString() {
        return super.toString() + ", reason("/* #Frozen */ + reason + ")"/* #Frozen */;
    }
}
