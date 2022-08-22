package ee.omnifish.transact.api;

import java.util.List;

import ee.omnifish.transact.api.spi.TransactionalResource;

/**
 * ResourceHandler provides interface to access resources handled by a component.
 */

public interface ResourceHandler {

    List<TransactionalResource> getResourceList();

}