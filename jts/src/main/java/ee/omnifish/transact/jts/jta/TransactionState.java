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

package ee.omnifish.transact.jts.jta;

import static ee.omnifish.transact.jts.CosTransactions.Configuration.isLocalFactory;
import static ee.omnifish.transact.jts.jtsxa.Utility.getXID;
import static jakarta.transaction.Status.STATUS_ACTIVE;
import static java.lang.System.arraycopy;
import static java.util.Collections.enumeration;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static javax.transaction.xa.XAResource.TMFAIL;
import static javax.transaction.xa.XAResource.TMJOIN;
import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMRESUME;
import static javax.transaction.xa.XAResource.TMSUCCESS;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.omg.CORBA.TRANSACTION_ROLLEDBACK;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.Inactive;
import org.omg.CosTransactions.Unavailable;

import ee.omnifish.transact.jts.CosTransactions.Configuration;
import ee.omnifish.transact.jts.CosTransactions.ControlImpl;
import ee.omnifish.transact.jts.CosTransactions.GlobalTID;
import ee.omnifish.transact.jts.codegen.jtsxa.OTSResource;
import ee.omnifish.transact.jts.jtsxa.OTSResourceImpl;
import ee.omnifish.transact.jts.jtsxa.XID;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;

/**
 * keep track of per-transaction state
 *
 * @author Tony Ng
 */
public class TransactionState {

    static Logger _logger = Logger.getLogger(TransactionState.class.getName());

    /**
     * various association states for an XAResource
     */
    private static final int NOT_EXIST = -1;
    private static final int ASSOCIATED = 0;
    private static final int NOT_ASSOCIATED = 1;
    private static final int ASSOCIATION_SUSPENDED = 2;
    private static final int FAILED = 3;
    private static final int ROLLING_BACK = 4;

    /**
     * a mapping of XAResource -> Integer (state) possible states are listed above
     */
    private Map<XAResource, Integer> resourceStates;

    /**
     * Map: XAResource -> Xid
     */
    private Map<XAResource, Xid> xaResources;

    /**
     * a set of Xid branches on which xa_start() has been called
     */
    private Set<Xid> seenXids;

    /**
     * a list of unique resource factory (represented by XAResource)
     */
    private List<XAResource> factories;

    // The OTS synchronization object for this transaction
    private SynchronizationImpl synchronizationImpl;

    private GlobalTID globalTID;
    private TransactionImpl transactionImpl;

    public TransactionState(GlobalTID gtid, TransactionImpl tran) {
        resourceStates = new HashMap<>();
        xaResources = new HashMap<>();
        seenXids = new HashSet<>();
        factories = new ArrayList<>();
        this.globalTID = gtid;
        this.transactionImpl = tran;
    }

    /**
     * this is called via callback of Synchronization right before a transaction commit or rollback to ensure that all
     * XAResources are properly delisted
     */
    synchronized public void beforeCompletion() {
        boolean exceptionThrown = false;
        XAResource res = null;
        Iterator e = resourceStates.keySet().iterator();
        while (e.hasNext()) {
            try {
                res = (XAResource) e.next();
                int XAState = getXAState(res);
                switch (XAState) {
                case NOT_ASSOCIATED:
                case FAILED:
                    break;
                case ASSOCIATION_SUSPENDED:
                case ASSOCIATED:
                    Xid xid = xaResources.get(res);
                    res.end(xid, TMSUCCESS);
                    setXAState(res, NOT_ASSOCIATED);
                    break;
                case ROLLING_BACK:
                case NOT_EXIST:
                default:
                    throw new IllegalStateException("Wrong XA State: " + XAState);
                }
            } catch (Exception ex) {
                setXAState(res, FAILED);
                _logger.log(WARNING, "jts.delist_exception", ex);
                exceptionThrown = true;
            }
        }

        if (exceptionThrown) {
            try {
                transactionImpl.setRollbackOnly();
            } catch (Exception ex) {
            }
        }
    }

    /**
     * This is called from OTS to rollback a particular XAResource
     */
    synchronized public void rollback(XAResource res) throws IllegalStateException, XAException {
        // Rollback the requested resource
        _rollback(res);

        // Now rollback all other resources known that are not
        // registered with the RegisteredResources during startAssociation() call
        Iterator e = resourceStates.keySet().iterator();
        while (e.hasNext()) {
            XAResource res0 = (XAResource) e.next();
            if (res0.isSameRM(res) && res0 != res) {
                _end(res0);
            }
        }
    }

    synchronized private void _rollback(XAResource xaResource) throws IllegalStateException, XAException {
        Xid xid = xaResources.get(xaResource);

        assert_prejdk14(xid != null);

        int XAState = getXAState(xaResource);
        switch (XAState) {
            case NOT_ASSOCIATED:
            case FAILED:
                xaResource.rollback(xid);
                break;
            case ASSOCIATION_SUSPENDED:
            case ASSOCIATED:
                try {
                    xaResource.end(xid, TMSUCCESS);
                } catch (Exception ex) {
                    _logger.log(WARNING, "jts.delist_exception", ex);
                }
                setXAState(xaResource, NOT_ASSOCIATED);
                xaResource.rollback(xid);
                /**
                 * was in ASSOCIATED: // rollback is deferred until delistment setXAState(res, ROLLING_BACK); activeResources++;
                 **/
                break;
            case ROLLING_BACK:
            case NOT_EXIST:
            default:
                throw new IllegalStateException("Wrong XAState: " + XAState);
        }
    }

    synchronized private void _end(XAResource xaResource) throws IllegalStateException, XAException {
        Xid xid = xaResources.get(xaResource);

        assert_prejdk14(xid != null);
        int XAState = getXAState(xaResource);
        switch (XAState) {
            case NOT_ASSOCIATED:
            case FAILED:
                // do nothing
                break;
            case ASSOCIATION_SUSPENDED:
            case ASSOCIATED:
                try {
                    xaResource.end(xid, TMSUCCESS);
                } catch (Exception ex) {
                    _logger.log(WARNING, "jts.delist_exception", ex);
                }
                setXAState(xaResource, NOT_ASSOCIATED);
                break;
            case ROLLING_BACK:
            case NOT_EXIST:
            default:
                throw new IllegalStateException("Wrong XAState: " + XAState);
        }
    }

    private Xid computeXid(XAResource xaResource, Control control) throws Inactive, Unavailable, XAException {
        // One branch id per RM
        int size = factories.size();
        for (int i = 0; i < size; i++) {
            XAResource fac = factories.get(i);
            if (xaResource.isSameRM(fac)) {
                // Use same branch
                Xid xid = xaResources.get(fac);
                return xid;
            }
        }

        // Use a different branch
        // XXX ideally should call JTS layer to get the branch id
        XID xid;

        if (isLocalFactory()) {
            xid = getXID(((ControlImpl) control).get_localCoordinator());
        } else {
            xid = getXID(control.get_coordinator());
        }
        factories.add(xaResource);

        byte[] branchid = parseSize(size);
        byte[] sname = Configuration.getServerNameByteArray();
        byte[] branch = new byte[sname.length + 1 + branchid.length];

        arraycopy(sname, 0, branch, 0, sname.length);
        branch[sname.length] = (byte) ',';
        arraycopy(branchid, 0, branch, sname.length + 1, branchid.length);

        xid.setBranchQualifier(branch);

        return xid;
    }

    synchronized public void startAssociation(XAResource xaResource, Control control, int status) throws XAException, SystemException, IllegalStateException, RollbackException {
        _logger.log(FINE, () -> "startAssociation for " + xaResource);

        OTSResource otsResource;

        try {
            // XXX should avoid using XID in JTA layer (but why?)
            Xid xid = null;
            boolean seenXid = false;
            if (xaResources.get(xaResource) == null) {
                if (_logger.isLoggable(FINE)) {
                    _logger.log(FINE, "startAssociation for unknown resource");
                }

                // Throw RollbackException if try to register a new resource when a transaction is marked
                // rollback
                if (status != STATUS_ACTIVE) {
                    throw new RollbackException();
                }

                xid = computeXid(xaResource, control);
                seenXid = seenXids.contains(xid);

                // Register with OTS
                if (!seenXid) {
                    // New branch: no need to activate OTSResource object since its local.
                    otsResource = new OTSResourceImpl(xid, xaResource, this);
                    if (isLocalFactory()) {
                        ((ControlImpl) control).get_localCoordinator().register_resource(otsResource);
                    } else {
                        control.get_coordinator().register_resource(otsResource);
                    }
                }

                xaResources.put(xaResource, xid);
            } else {
                _logger.log(FINE, "startAssociation for known resource");

                // Use the previously computed branch id
                xid = xaResources.get(xaResource);
                seenXid = seenXids.contains(xid);
            }

            int XAState = getXAState(xaResource);
            _logger.log(FINE, () -> "startAssociation in state: " + XAState);

            if (!seenXid) {
                // First time this branch is enlisted
                seenXids.add(xid);
                xaResource.start(xid, TMNOFLAGS);
                setXAState(xaResource, ASSOCIATED);
            } else {
                // have seen this branch before
                switch (XAState) {
                case NOT_ASSOCIATED:
                case NOT_EXIST:
                    xaResource.start(xid, TMJOIN);
                    setXAState(xaResource, ASSOCIATED);
                    break;
                case ASSOCIATION_SUSPENDED:
                    xaResource.start(xid, TMRESUME);
                    setXAState(xaResource, ASSOCIATED);
                    break;
                case ASSOCIATED:
                case FAILED:
                case ROLLING_BACK:
                default:
                    throw new IllegalStateException("Wrong XAState: " + XAState);
                }
            }
        } catch (XAException ex) {
            setXAState(xaResource, FAILED);
            throw ex;
        } catch (Inactive ex) {
            _logger.log(WARNING, "jts.transaction_inactive", ex);
            throw new SystemException();
        } catch (Unavailable ex) {
            _logger.log(WARNING, "jts.object_unavailable", ex);
            throw new SystemException();
        }
    }

    synchronized public void endAssociation(XAResource xaResource, int flags) throws XAException, IllegalStateException {
        _logger.log(FINE, () -> "endAssociation for " + xaResource);

        try {
            Xid xid = xaResources.get(xaResource);
            assert_prejdk14(xid != null);
            int XAState = getXAState(xaResource);
            _logger.log(FINE, () -> "endAssociation in state: " + XAState);

            switch (XAState) {
            case ASSOCIATED:
                if ((flags & TMSUCCESS) != 0) {
                    xaResource.end(xid, TMSUCCESS);
                    setXAState(xaResource, NOT_ASSOCIATED);
                } else if ((flags & XAResource.TMSUSPEND) != 0) {
                    xaResource.end(xid, XAResource.TMSUSPEND);
                    setXAState(xaResource, ASSOCIATION_SUSPENDED);
                } else {
                    xaResource.end(xid, TMFAIL);
                    setXAState(xaResource, FAILED);
                }
                break;
            case ROLLING_BACK:
                // rollback deferred XAResources
                xaResource.end(xid, TMSUCCESS);
                setXAState(xaResource, NOT_ASSOCIATED);
                xaResource.rollback(xid);

                break;
            case ASSOCIATION_SUSPENDED:
                if ((flags & TMSUCCESS) != 0) {
                    xaResource.end(xid, TMSUCCESS);
                    setXAState(xaResource, NOT_ASSOCIATED);
                } else if ((flags & XAResource.TMSUSPEND) != 0) {
                    throw new IllegalStateException("Wrong XAState: " + XAState);
                } else {
                    xaResource.end(xid, TMFAIL);
                    setXAState(xaResource, FAILED);
                }
                break;
            case NOT_ASSOCIATED:
            case NOT_EXIST:
            case FAILED:
            default:
                throw new IllegalStateException("Wrong XAState: " + XAState);
            }
        } catch (XAException ex) {
            setXAState(xaResource, FAILED);
            throw ex;
        }
    }

    // To be called by SynchronizationImpl, if there are any exception
    // in beforeCompletion() call backs
    void setRollbackOnly() throws IllegalStateException, SystemException {
        transactionImpl.setRollbackOnly();
    }

    synchronized public void registerSynchronization(Synchronization sync, Control control, boolean interposed) throws RollbackException, IllegalStateException, SystemException {
        try {
            // One OTS Synchronization object per transaction
            if (synchronizationImpl == null) {
                synchronizationImpl = new SynchronizationImpl(this);

                // SyncImpl is a local object. No need to activate it.
                if (isLocalFactory()) {
                    ((ControlImpl) control).get_localCoordinator().register_synchronization(synchronizationImpl);
                } else {
                    control.get_coordinator().register_synchronization(synchronizationImpl);
                }
            }
            synchronizationImpl.addSynchronization(sync, interposed);
        } catch (TRANSACTION_ROLLEDBACK ex) {
            throw new RollbackException();
        } catch (Unavailable ex) {
            _logger.log(WARNING, "jts.object_unavailable", ex);
            throw new SystemException();
        } catch (Inactive ex) {
            _logger.log(WARNING, "jts.transaction_inactive", ex);
            throw new IllegalStateException();
        } catch (Exception ex) {
            _logger.log(WARNING, "jts.exception_in_register_synchronization", ex);
            throw new SystemException();
        }
    }

    private void setXAState(XAResource xaResource, Integer state) {
        if (_logger.isLoggable(FINE)) {
            int oldValue = getXAState(xaResource);
            _logger.log(FINE, "transaction id : " + globalTID);
            _logger.log(FINE, "res: " + xaResource + ", old state: " + oldValue + ", new state: " + state);
        }

        resourceStates.put(xaResource, state);
    }

    private int getXAState(XAResource xaResource) {
        Integer result = resourceStates.get(xaResource);
        if (result == null) {
            return NOT_EXIST;
        }

        return result;
    }

    /**
     * list all the XAResources that have been enlisted in this transaction.
     */
    public Enumeration<XAResource> listXAResources() {
        return enumeration(xaResources.keySet());
    }

    /**
     * return true if res has been enlisted in this transaction; false otherwise.
     */
    public boolean containsXAResource(XAResource res) {
        return xaResources.containsKey(res);
    }

    static private void assert_prejdk14(boolean value) {
        if (!value) {
            Exception e = new Exception();
            _logger.log(WARNING, "jts.assert", e);
        }
    }

    private static byte[] parseSize(int size) {
        switch (size) {
        case 0:
            return new byte[] { 0 };
        case 1:
            return new byte[] { 1 };
        case 2:
            return new byte[] { 2 };
        case 3:
            return new byte[] { 3 };
        case 4:
            return new byte[] { 4 };
        case 5:
            return new byte[] { 5 };
        case 6:
            return new byte[] { 6 };
        case 7:
            return new byte[] { 7 };
        case 8:
            return new byte[] { 8 };
        case 9:
            return new byte[] { 9 };
        }

        int j = 9;
        byte[] res = new byte[10];
        while (size > 0) {
            res[j--] = (byte) (size % 10);
            size = size / 10;
        }
        int len = 9 - j;
        byte[] result = new byte[len];
        System.arraycopy(res, j + 1, result, 0, len);
        return result;
    }

}
