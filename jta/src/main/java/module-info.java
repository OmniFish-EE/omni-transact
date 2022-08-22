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
module org.omnifish.transact.jta {

    exports org.omnifish.transact.jta.cache;
    opens org.omnifish.transact.jta.cache;
    
    exports org.omnifish.transact.jta.cdi;
    opens org.omnifish.transact.jta.cdi;
    
    exports org.omnifish.transact.jta.transaction;
    opens org.omnifish.transact.jta.transaction;
    requires jakarta.transaction;
    requires java.logging;
    requires org.omnifish.transact.api;
    requires java.rmi;
    
}
