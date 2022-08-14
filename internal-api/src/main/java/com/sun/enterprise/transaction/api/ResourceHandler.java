package com.sun.enterprise.transaction.api;

import java.util.List;

import com.sun.enterprise.transaction.spi.TransactionalResource;

/**
 * ResourceHandler provides interface to access resources handled by a component.
 */

public interface ResourceHandler {

    List<TransactionalResource> getResourceList();

}