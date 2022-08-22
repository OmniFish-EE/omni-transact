package ee.omnifish.transact.api.spi;

import java.lang.annotation.Annotation;
import java.util.List;


public interface ServiceLocator {

    /**
     * Gets the best service from this locator that implements
     * this contract or has this implementation
     * <p>
     * Use this method only if destroying the service is not important,
     * otherwise use <code>ServiceLocator#getServiceHandle(Class, Annotation...)</code>
     *
     * @param contractOrImpl May not be null, and is the contract
     * or concrete implementation to get the best instance of
     * @param qualifiers The set of qualifiers that must match this service
     * definition
     * @return An instance of the contract or impl.  May return
     * null if there is no provider that provides the given
     * implementation or contract
     */
    <T> T getService(Class<T> contractOrImpl, Annotation... qualifiers);

    /**
     * Gets the best service from this locator that implements
     * this contract or has this implementation and has the given
     * name
     * <p>
     * Use this method only if destroying the service is not important,
     * otherwise use <code>ServiceLocator#getServiceHandle(Class, String, Annotation...)</code>
     *
     * @param contractOrImpl May not be null, and is the contract
     * or concrete implementation to get the best instance of
     * @param name May be null (to indicate any name is ok), and is the name of the
     * implementation to be returned
     * @param qualifiers The set of qualifiers that must match this service
     * definition
     * @return An instance of the contract or impl.  May return
     * null if there is no provider that provides the given
     * implementation or contract
     */
    <T> T getService(Class<T> contractOrImpl, String name, Annotation... qualifiers);

    /**
     * Gets all services from this locator that implement this contract or have this
     * implementation and have the provided qualifiers
     * <p>
     * Use this method only if destroying the service is not important,
     * otherwise use <code>ServiceLocator#getAllServiceHandles(Class, Annotation...)</code>
     *
     * @param contractOrImpl May not be null, and is the contract
     * or concrete implementation to get the best instance of
     * @param qualifiers The set of qualifiers that must match this service
     * definition
     * @return A list of services implementing this contract
     * or concrete implementation.  May not return null, but
     * may return an empty list
     */
    <T> List<T> getAllServices(Class<T> contractOrImpl, Annotation... qualifiers);

}
