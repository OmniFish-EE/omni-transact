/*
 * Copyright (c) 2022, 2022 OmniFish and/or its affiliates. All rights reserved.
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
package ee.omnifish.transact.cdi.beans;

import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

/**
 *
 * @author Arjan Tijms
 *
 */
public class JndiToCdi implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        BeanManager beanManager = CDI.current().getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(name.toString());
        if (beans.size() != 1) {
            return null;
        }

        Bean<?> bean = beans.iterator().next();
        return beanManager.getReference(bean, bean.getTypes().iterator().next(), beanManager.createCreationalContext(bean));
    }

}
