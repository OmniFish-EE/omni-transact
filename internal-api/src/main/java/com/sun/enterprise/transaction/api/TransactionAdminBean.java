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

package com.sun.enterprise.transaction.api;

import java.io.Serializable;
import java.util.List;

public class TransactionAdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private Object identifier;
    private String id;
    private String status;
    private long elapsedTime;
    private String componentName;
    private List<String> resourceNames;

    public TransactionAdminBean(Object identifier, String id, String status, long elapsedTime, String componentName, List<String> resourceNames) {
        this.identifier = identifier;
        this.id = id;
        this.status = status;
        this.elapsedTime = elapsedTime;
        this.componentName = componentName;
        this.resourceNames = resourceNames;
    }

    // getter functions ...

    public Object getIdentifier() {
        return identifier;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public String getComponentName() {
        return componentName;
    }

    public List<String> getResourceNames() {
        return resourceNames;
    }

    // setter functions ...

    public void setIdentifier(Object id) {
        identifier = id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setStatus(String sts) {
        status = sts;
    }

    public void setElapsedTime(long time) {
        elapsedTime = time;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public void setResourceNames(List<String> resourceNames) {
        this.resourceNames = resourceNames;
    }

}
