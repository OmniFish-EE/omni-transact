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
// Module:      Messages_en_US.java
//
// Description: JTS messages, US English (for testing purposes).
//
// Product:     ee.omnifish.transact.jts.CosTransactions
//
// Author:      Simon Holdsworth
//
// Date:        March, 1997
//----------------------------------------------------------------------------

package ee.omnifish.transact.jts.CosTransactions;

/**
 * This class provides a ListResourceBundle which contains the message formats for messages produced by the JTS. It is
 * an example which can be copied for other languages.
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

public class Messages_en_US extends Messages {

    /**
     * Return the contents of the bundle.
     */
    @Override
    protected Object[][] getContents() {
        return contents;
    }

    /**
     * The message formats.
     */
    private static final Object[][] contents = {

            // Required messages.

            { "", "Unknown message number {0}." }, { "", "Invalid message format for message number {0}." },

            // Application messages.

            { "000", "The ORB daemon, ORBD, is not running." },
            { "001", "(US) This is a non-persistent server. " + "Transactions will not be recoverable." },
            { "002", "Cannot register {0} instance with the ORB." }, { "003", "Cannot initialise log." },
            { "004", "Cannot open log file for server {0}." }, { "005", "Cannot create {0} object reference." },
            { "006", "Cannot destroy {0} object reference." }, { "007", "Already identified to communications manager." },
            { "008", "Unable to identify to communications manager." }, { "009", "Unable to create a subordinate Coordinator." },
            { "010", "Exception {0} recovering an in-doubt Coordinator." }, { "011", "Exception {0} on {1} operation during resync." },
            { "012", "Exception {0} on {1} resource operation." }, { "013", "Retry limit of {0} {1} operations exceeded." },
            { "014", "Exception {0} on {1} synchronization operation." }, { "015", "Timeout thread stopped." },
            { "016", "Invalid log path.  Using {0}." }, { "017", "Invalid default log path.  Using current directory." },
            { "018", "Cannot access server information for server {0}." }, { "019", "Cannot access global information." }, { "020", "" },
            { "021", "Unexpected exception {0} from log." }, { "022", "Defaulting to current directory for trace." },
            { "023", "Invalid repository path.  Using {0}." }, { "024", "Invalid default repository path. Using current directory." },
            { "025", "Cannot read repository file." }, { "026", "Cannot locate {0} servant." },
            { "027", "Heuristic exception {0} cannot be reported to superior " + "in resync." },
            { "028", "Wait for resync complete interrupted." }, { "029", "Transaction in the wrong state for {0} operation." },
            { "030", "Unable to store information in repository." }, { "031", "xa_close operation failed during recovery." },
            { "032", "Could not reconstruct XA information during recovery." }, { "033", "XA Resource Manager string {0} is invalid." },
            { "034", "XA Resource Manager {1} initialisation failed." },
            { "035", "{0} with string {1} returned {2} for " + "Resource Manager {3}." },
            { "036", "{0} operation returned {1} for Resource Manager {2}." },
            { "037", "Incorrect {0} context during transaction start " + "association." },
            { "038", "SQL error: {0} returned rc {1}, SQLCODE {2}." },
            { "039", "Unexpected exception ''{0}'' while loading XA switch " + "class {1}." },
            { "040", "Connot allocate SQL environment." }, { "041", "Unable to create new JDBC-ODBC Driver." },
            { "042", "Security check failed, reason string is {0}." }, { "043", "Unable to create new JdbcOdbc instance." },
            { "044", "Unable to register with the JDBC Driver Manager." }, { "045", "Unable to load JDBC-ODBC Driver class." },
            { "046", "Unexpected exception ''{0}'' while loading class {1}." }, { "047", "Log file exists for transient server {0}." },
            { "048", "Invalid log record data in section {0}." },
            { "049", "No entry found for database ''{0}'' in the ODBC " + "configuration." },
            { "050", "ODBC Driver ''{0}'' for database ''{1}'' not supported " + "for transactional connection." },
            { "051", "Unable to convert object reference to string in recovery." },
            { "052", "Transaction resynchronization from originator failed, " + "retrying...." },
            { "053", "Transaction id is already in use." }, { "054", "Invalid transaction state change." },
            { "055", "No coordinator available." }, { "056", "XAException occured during recovery of XAResource objects." },
            { "057", "Recoverable JTS instance, serverId = {0}." }, { "058", "No server name." },
            { "059", "Error: Unable to write to error log file." }, { "060", "JTS Error: {0}." }, { "061", "JTS Warning: {0}." },
            { "062", "JTS Info: {0}." }, { "063", "{0} : {1} : JTS{2}{3} {4}\n" },
            { "064", "Invalid timeout value. Negative values are illegal." }, };
}
