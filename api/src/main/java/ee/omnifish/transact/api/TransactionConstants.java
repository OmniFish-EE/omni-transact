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

package ee.omnifish.transact.api;

/**
 * Transaction constants that can be referenced by other components.
 */
public interface TransactionConstants {
    // LAO_PREPARE_OK is a magic number. Assimung no valid XAResource.prepare returns
    // 123456 as a return value. LAO resource'd prepare method returns this value
    int LAO_PREPARE_OK = 123456;

}
