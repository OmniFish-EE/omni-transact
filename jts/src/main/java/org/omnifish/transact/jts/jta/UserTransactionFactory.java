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

package org.omnifish.transact.jts.jta;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * Factory for producing the UserTransactionImpl objects.
 *
 * @author Ram Jeyaraman
 * @version 1.0 Feb 09, 1999
 */
public class UserTransactionFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object refObj, Name name, Context nameCtx, Hashtable<?,?> env) throws Exception {
        if (refObj == null || !(refObj instanceof Reference)) {
            return null;
        }

        Reference ref = (Reference) refObj;

        if (ref.getClassName().equals(UserTransactionImpl.class.getName())) {
            // create a new object
            return new UserTransactionImpl();
        }

        return null;
    }
}
