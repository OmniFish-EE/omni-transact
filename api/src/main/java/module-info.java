
/**
 * @author Arjan Tijms
 */
module ee.omnifish.transact.api {

    exports ee.omnifish.transact.api;
    opens ee.omnifish.transact.api;
    
    exports ee.omnifish.transact.api.impl;
    opens ee.omnifish.transact.api.impl;
    
    exports ee.omnifish.transact.api.spi;
    opens ee.omnifish.transact.api.spi;
    
    requires transitive java.transaction.xa;
    requires transitive jakarta.transaction;
    requires transitive jakarta.resource;
    requires transitive jakarta.persistence;

    requires java.desktop;
    requires java.rmi;
    requires glassfish.corba.omgapi;
}
