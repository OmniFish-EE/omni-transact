package ee.omnifish.transact.api.impl;

import java.util.ArrayList;
import java.util.List;

import ee.omnifish.transact.api.ComponentInvocation;
import ee.omnifish.transact.api.ComponentInvocation.ComponentInvocationType;
import ee.omnifish.transact.api.InvocationManager;

public class InvocationManagerImpl implements InvocationManager {

    // This TLS variable stores an ArrayList.
    // The ArrayList contains ComponentInvocation objects which represent
    // the stack of invocations on this thread. Accesses to the ArrayList
    // dont need to be synchronized because each thread has its own ArrayList.
    private InheritableThreadLocal<InvocationArray<ComponentInvocation>> frames;

    public InvocationManagerImpl() {
        frames = new InheritableThreadLocal<>() {
            @Override
            protected InvocationArray<ComponentInvocation> initialValue() {
                return new InvocationArray<>();
            }

            // If this is a thread created by user in a servlet's service method
            // create a new ComponentInvocation with transaction set to null and
            // instance set to null so that the resource won't be enlisted or registered
            @Override
            protected InvocationArray<ComponentInvocation> childValue(InvocationArray<ComponentInvocation> parentValue) {
                // Always creates a new ArrayList
                InvocationArray<ComponentInvocation> result = new InvocationArray<>();
                InvocationArray<ComponentInvocation> v = parentValue;
                if (v.size() > 0 && v.outsideStartup()) {
                    // Get current invocation
                    ComponentInvocation parentInv = v.get(v.size() - 1);
                }

                return result;
            }
        };
    }

    /**
     * return true iff no invocations on the stack for this thread
     */
    @Override
    public boolean isInvocationStackEmpty() {
        List<ComponentInvocation> v = frames.get();
        return v == null || v.size() == 0;
    }

    /**
     * return the Invocation object of the component being called
     */
    @Override
    public <T extends ComponentInvocation> T getCurrentInvocation() {
        ArrayList<ComponentInvocation> v = frames.get();
        int size = v.size();
        if (size == 0) {
            return null;
        }

        return (T) v.get(size - 1);
    }

    @Override
    public <T extends ComponentInvocation> void preInvoke(T inv) {
        InvocationArray<ComponentInvocation> v = frames.get();
        if (inv.getInvocationType() == ComponentInvocationType.SERVICE_STARTUP) {
            v.setInvocationAttribute(ComponentInvocationType.SERVICE_STARTUP);
            return;
        }

        // push this invocation on the stack
        v.add(inv);
    }

    @Override
    public <T extends ComponentInvocation> void postInvoke(T inv) {
        // Get this thread's ArrayList
        InvocationArray<ComponentInvocation> v = frames.get();
        if (inv.getInvocationType() == ComponentInvocationType.SERVICE_STARTUP) {
            v.setInvocationAttribute(ComponentInvocationType.UN_INITIALIZED);
            return;
        }

        int beforeSize = v.size();
        if (beforeSize == 0) {
            throw new IllegalStateException();
        }


        // pop the stack
        v.remove(beforeSize - 1);
    }

    static class InvocationArray<T extends ComponentInvocation> extends ArrayList<T> {

        private static final long serialVersionUID = 1L;
        private ComponentInvocationType invocationAttribute;

        public void setInvocationAttribute(ComponentInvocationType attribute) {
            this.invocationAttribute = attribute;
        }

        public ComponentInvocationType getInvocationAttribute() {
            return invocationAttribute;
        }

        public boolean outsideStartup() {
            return getInvocationAttribute() != ComponentInvocationType.SERVICE_STARTUP;
        }
    }

}
