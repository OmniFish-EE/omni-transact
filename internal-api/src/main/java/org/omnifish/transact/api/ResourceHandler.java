package org.omnifish.transact.api;

import java.util.List;

import org.omnifish.transact.api.spi.TransactionalResource;

/**
 * ResourceHandler provides interface to access resources handled by a component.
 */

public interface ResourceHandler {

    List<TransactionalResource> getResourceList();

}