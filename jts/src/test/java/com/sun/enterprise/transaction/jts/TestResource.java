package com.sun.enterprise.transaction.jts;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.omnifish.transact.jta.transaction.JavaEETransactionManagerImpl;

import jakarta.transaction.Status;
import jakarta.transaction.Transaction;

public class TestResource implements XAResource {

    // allow only one resource in use at a time
    private boolean inUse;
    private boolean _forgetCalled = false;
    private boolean _isHeuristic = false;

    private int commitErrorCode = 9999;
    private int rollbackErrorCode = 9999;
    private int prepareErrorCode = 9999;
    private int startErrorCode = 9999;

    private Transaction tx;
    private int commit_status = -1;
    private int rollback_status = -1;
    private int prepare_status = -1;

    private static long id0 = System.currentTimeMillis();
    private long id = ++id0;

    TestResource() {
    }

    TestResource(Transaction tx) {
        this.tx = tx;

    }

    TestResource(Transaction tx, long id) {
        this.tx = tx;
        this.id = id;
    }

    // to test different xaexception error codes
    public void setCommitErrorCode(int errorCode) {
        this.commitErrorCode = errorCode;
        setHeuristic(errorCode);
    }

    public void setStartErrorCode(int errorCode) {
        this.startErrorCode = errorCode;
    }

    public void setRollbackErrorCode(int errorCode) {
        this.rollbackErrorCode = errorCode;
        setHeuristic(errorCode);
    }

    public void setPrepareErrorCode(int errorCode) {
        this.prepareErrorCode = errorCode;
    }

    private void setHeuristic(int errorCode) {
        if (errorCode == XAException.XA_HEURCOM || errorCode == XAException.XA_HEURHAZ || errorCode == XAException.XA_HEURMIX
                || errorCode == XAException.XA_HEURRB) {
            _isHeuristic = true;
        }
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        // test goes here
        System.out.println("in XA commit");
        commit_status = getStatus("COMMIT");
        if (commitErrorCode != 9999) {
            System.out.println("throwing XAException." + commitErrorCode + " during commit of " + (onePhase ? "1" : "2") + "pc");
            throw new XAException(commitErrorCode);
        }
    }

    @Override
    public boolean isSameRM(XAResource xaresource) throws XAException {
        return xaresource == this || this.id == ((TestResource) xaresource).id;
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        System.out.println("in XA rollback");
        rollback_status = getStatus("ROLLBACK");
        if (rollbackErrorCode != 9999) {
            System.out.println("throwing XAException." + rollbackErrorCode + " during rollback");
            throw new XAException(rollbackErrorCode);
        }
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        System.out.println("in XA prepare");
        prepare_status = getStatus("PREPARE");
        if (prepareErrorCode != 9999) {
            System.out.println("throwing XAException." + prepareErrorCode + " during prepare");
            throw new XAException(prepareErrorCode);
        }
        return XAResource.XA_OK;
    }

    @Override
    public boolean setTransactionTimeout(int i) throws XAException {
        return true;
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    @Override
    public void forget(Xid xid) throws XAException {
        _forgetCalled = true;
        inUse = false;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        if (inUse) {
            throw new XAException(XAException.XAER_NOTA);
        }
        inUse = true;
        if (startErrorCode != 9999) {
            System.out.println("throwing XAException." + startErrorCode + " during start");
            throw new XAException(startErrorCode);
        }
    }

    @Override
    public void end(Xid xid, int flags) throws XAException {
        inUse = false;
    }

    @Override
    public Xid[] recover(int flags) throws XAException {
        return null;
    }

    public boolean forgetCalled() {
        return !_isHeuristic || _forgetCalled;
    }

    public boolean commitStatusOK() {
        return commit_status == Status.STATUS_COMMITTING;
    }

    public boolean rollbackStatusOK() {
        return rollback_status == Status.STATUS_ROLLING_BACK;
    }

    public boolean prepareStatusOK() {
        return prepare_status == Status.STATUS_PREPARING;
    }

    public boolean isAssociated() {
        return inUse;
    }

    private int getStatus(String name) {
        int status = -1;
        try {
            if (tx != null) {
                status = tx.getStatus();
                System.out.println("Status in " + name + ": " + JavaEETransactionManagerImpl.getStatusAsString(status));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return status;
    }

}
