
/**
 * @author Arjan Tijms
 */
module org.omnifish.transact.api {

    exports org.omnifish.transact.api;
    opens org.omnifish.transact.api;
    
    exports org.omnifish.transact.api.impl;
    opens org.omnifish.transact.api.impl;
    
    exports org.omnifish.transact.api.spi;
    opens org.omnifish.transact.api.spi;
    
    requires transitive java.transaction.xa;
    requires transitive jakarta.transaction;
    requires transitive jakarta.resource;
    requires transitive jakarta.persistence;

    requires java.desktop;
    requires java.rmi;
    requires glassfish.corba.omgapi;
}
