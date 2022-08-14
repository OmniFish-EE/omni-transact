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

import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.OutputStream;

/**
 * com/sun/jts/codegen/otsidl/ResourceStatusHelper.java . Generated by the IDL-to-Java compiler (portable), version
 * "3.1" from com/sun/jts/ots.idl Tuesday, February 5, 2002 12:57:23 PM PST
 */

abstract public class ResourceStatusHelper {
    private static String _id = "IDL:otsidl/ResourceStatus:1.0";

    public static void insert(Any a, ResourceStatus that) {
        OutputStream out = a.create_output_stream();
        a.type(type());
        write(out, that);
        a.read_value(out.create_input_stream(), type());
    }

    public static ResourceStatus extract(Any a) {
        return read(a.create_input_stream());
    }

    private static TypeCode __typeCode = null;

    synchronized public static TypeCode type() {
        if (__typeCode == null) {
            __typeCode = ORB.init().create_enum_tc(ResourceStatusHelper.id(), "ResourceStatus",
                    new String[] { "None", "Registered", "Completing", "Completed", "Heuristic" });
        }
        return __typeCode;
    }

    public static String id() {
        return _id;
    }

    public static ResourceStatus read(org.omg.CORBA.portable.InputStream istream) {
        return ResourceStatus.from_int(istream.read_long());
    }

    public static void write(OutputStream ostream, ResourceStatus value) {
        ostream.write_long(value.value());
    }

}
