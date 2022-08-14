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

package com.sun.jts.codegen.otsidl;

import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.Streamable;

/**
 * com/sun/jts/codegen/otsidl/ResourceStatusHolder.java . Generated by the IDL-to-Java compiler (portable), version
 * "3.1" from com/sun/jts/ots.idl Tuesday, February 5, 2002 12:57:23 PM PST
 */

public final class ResourceStatusHolder implements Streamable {
    public ResourceStatus value;

    public ResourceStatusHolder() {
    }

    public ResourceStatusHolder(ResourceStatus initialValue) {
        value = initialValue;
    }

    @Override
    public void _read(InputStream i) {
        value = ResourceStatusHelper.read(i);
    }

    @Override
    public void _write(org.omg.CORBA.portable.OutputStream o) {
        ResourceStatusHelper.write(o, value);
    }

    @Override
    public TypeCode _type() {
        return ResourceStatusHelper.type();
    }

}
