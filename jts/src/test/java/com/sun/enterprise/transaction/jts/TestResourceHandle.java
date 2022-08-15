package com.sun.enterprise.transaction.jts;

import javax.transaction.xa.XAResource;

import com.sun.enterprise.transaction.spi.TransactionalResource;

import jakarta.transaction.Transaction;

public class TestResourceHandle implements TransactionalResource {

    private final XAResource resource;

    public TestResourceHandle(XAResource resource) {
        this.resource = resource;
    }

    @Override
    public boolean isTransactional() {
        return true;
    }

    @Override
    public boolean isShareable() {
        return true;
    }

    @Override
    public boolean supportsXA() {
        return true;
    }

    @Override
    public XAResource getXAResource() {
        return resource;
    }

    @Override
    public void enlistedInTransaction(Transaction tran) throws IllegalStateException {
    }

    @Override
    public boolean isEnlistmentSuspended() {
        return false;
    }

    @Override
    public Object getComponentInstance() {
        return null;
    }

    @Override
    public void setComponentInstance(Object instance) {

    }

    @Override
    public void closeUserConnection() throws Exception {

    }

    @Override
    public boolean isEnlisted() {
        return false;
    }

    @Override
    public void destroyResource() {

    }

    @Override
    public String getName() {
        return null;
    }
}