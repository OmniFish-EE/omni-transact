/*
 * Copyright (c) 2022, 2022 OmniFish and/or its affiliates. All rights reserved.
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

/**
 * @author Arjan Tijms
 */
module org.omnifish.transact.jts {

    exports org.omnifish.transact.jts;
    exports org.omnifish.transact.jts.api;
    exports org.omnifish.transact.jts.codegen.jtsxa;
    exports org.omnifish.transact.jts.codegen.otsidl;
    exports org.omnifish.transact.jts.CosTransactions;
    exports org.omnifish.transact.jts.jta;
    exports org.omnifish.transact.jts.jtsxa;
    exports org.omnifish.transact.jts.pi;
    exports org.omnifish.transact.jts.trace;
    exports org.omnifish.transact.jts.utils;
    exports org.omnifish.transact.jts.utils.RecoveryHooks;
    requires java.logging;
    requires java.transaction.xa;
    requires org.omnifish.transact.api;
    requires org.omnifish.transact.jta;
    requires glassfish.corba.omgapi;
    
}
