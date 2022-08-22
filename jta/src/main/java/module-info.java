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
module ee.omnifish.transact.jta {

    exports ee.omnifish.transact.jta.cache;
    opens ee.omnifish.transact.jta.cache;
    
    exports ee.omnifish.transact.jta.cdi;
    opens ee.omnifish.transact.jta.cdi;
    
    exports ee.omnifish.transact.jta.transaction;
    opens ee.omnifish.transact.jta.transaction;
    requires jakarta.transaction;
    requires java.logging;
    requires ee.omnifish.transact.api;
    requires java.rmi;
    
}
