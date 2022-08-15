package org.omnifish.transact.api.api;

public class InvocationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvocationException() {
    }

    public InvocationException(String s) {
        super(s);
    }

    public InvocationException(Exception ex) {
        super(ex);
    }

}