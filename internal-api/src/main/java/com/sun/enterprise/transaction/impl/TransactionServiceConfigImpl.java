package com.sun.enterprise.transaction.impl;

import java.beans.PropertyVetoException;
import java.util.List;
import java.util.Map;

import com.sun.enterprise.transaction.api.TransactionServiceConfig;


public class TransactionServiceConfigImpl implements TransactionServiceConfig {

    private List<Map.Entry<String, String>> properties = getProperties();

    @Override
    public String getAutomaticRecovery() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setAutomaticRecovery(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getTimeoutInSeconds() {
        return "0";
    }

    @Override
    public void setTimeoutInSeconds(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getTxLogDir() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setTxLogDir(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getHeuristicDecision() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setHeuristicDecision(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getRetryTimeoutInSeconds() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setRetryTimeoutInSeconds(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getKeypointInterval() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setKeypointInterval(String value) throws PropertyVetoException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getPropertyValue(String key) {
        // TODO Auto-generated method stub
        for (Map.Entry<String, String> entry : properties) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }

        return null;
    }

}
