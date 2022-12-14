package ee.omnifish.transact.api;

import ee.omnifish.transact.api.spi.ServiceLocator;

public class Globals {

    private static volatile ServiceLocator defaultServiceLocator;

    public static ServiceLocator getDefaultServiceLocator() {
        return defaultServiceLocator;
    }

    public static <T> T get(Class<T> type) {
        return defaultServiceLocator.getService(type);
    }

    public static void setDefaultServiceLocator(ServiceLocator serviceLocator) {
        defaultServiceLocator = serviceLocator;
    }

}