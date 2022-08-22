package ee.omnifish.transact.api;

public interface ComponentInvocation {

    public enum ComponentInvocationType {
        SERVLET_INVOCATION, EJB_INVOCATION, APP_CLIENT_INVOCATION, UN_INITIALIZED, SERVICE_STARTUP
    }

    ComponentInvocationType getInvocationType();

    Object getTransaction();
    void setTransaction(Object transaction);

    boolean isTransactionCompleting();
    void setTransactionCompeting(boolean completing);

    Object getInstance();
    Object getResourceTableKey();
    Object getTransactionOperationsManager();
    void setTransactionOperationsManager(Object o);

    ResourceHandler getResourceHandler();

}
