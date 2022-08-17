package org.omnifish.transact.api.impl;

import org.omnifish.transact.api.ComponentInvocation;
import org.omnifish.transact.api.ResourceHandler;

public class ComponentInvocationImpl implements ComponentInvocation {

    Object instance;
    ComponentInvocationType componentInvocationType;

    Object transaction;
    boolean completing;

    Object transactionOperationsManager;

    public ComponentInvocationImpl(Object instance, ComponentInvocationType componentInvocationType) {
        this.instance = instance;
        this.componentInvocationType = componentInvocationType;
    }

    @Override
    public ComponentInvocationType getInvocationType() {
        return componentInvocationType;
    }

    @Override
    public Object getInstance() {
        return componentInvocationType;
    }

    @Override
    public Object getTransaction() {
        return transaction;
    }

    @Override
    public void setTransaction(Object transaction) {
        this.transaction = transaction;

    }

    @Override
    public boolean isTransactionCompleting() {
        return completing;
    }

    @Override
    public void setTransactionCompeting(boolean completing) {
        this.completing = completing;

    }

    @Override
    public Object getResourceTableKey() {
        return null;
    }

    @Override
    public Object getTransactionOperationsManager() {
        return transactionOperationsManager;
    }

    @Override
    public void setTransactionOperationsManager(Object transactionOperationsManager) {
        this.transactionOperationsManager = transactionOperationsManager;

    }

    @Override
    public ResourceHandler getResourceHandler() {
        return null;
    }

}
