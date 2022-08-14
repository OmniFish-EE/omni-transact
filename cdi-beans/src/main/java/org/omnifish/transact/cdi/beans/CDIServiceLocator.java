package org.omnifish.transact.cdi.beans;

import static java.util.stream.Collectors.toList;

import java.lang.annotation.Annotation;
import java.util.List;

import com.sun.enterprise.transaction.spi.ServiceLocator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;

@ApplicationScoped
public class CDIServiceLocator implements ServiceLocator {

    @Override
    public <T> T getService(Class<T> contractOrImpl, Annotation... qualifiers) {
        return CDI.current().select(contractOrImpl, qualifiers).get();
    }

    @Override
    public <T> T getService(Class<T> contractOrImpl, String name, Annotation... qualifiers) {
        // TODO : process name
        return CDI.current().select(contractOrImpl, qualifiers).get();
    }

    @Override
    public <T> List<T> getAllServices(Class<T> contractOrImpl, Annotation... qualifiers) {
        return CDI.current().select(contractOrImpl, qualifiers).stream().collect(toList());
    }

}