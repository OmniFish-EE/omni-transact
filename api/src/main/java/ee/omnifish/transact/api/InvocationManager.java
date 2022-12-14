package ee.omnifish.transact.api;

public interface InvocationManager {

    /**
     * Returns the current Invocation object associated with the current thread
     */
    <T extends ComponentInvocation> T getCurrentInvocation();

    /**
     * return true iff no invocations on the stack for this thread
     */
    boolean isInvocationStackEmpty();

    public <T extends ComponentInvocation> void preInvoke(T inv);

    public <T extends ComponentInvocation> void postInvoke(T inv);
}