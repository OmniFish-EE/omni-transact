package org.omnifish.transact.api.api;

import org.omg.CORBA.TSIdentification;
import org.omg.CosTSPortability.Receiver;
import org.omg.CosTSPortability.Sender;

public class Corba {

    public static boolean isProxy(Object obj) {

        return true;

        // return !(StubAdapter.isStub(obj) && StubAdapter.isLocal(obj));
    }

    public static Sender getSender(TSIdentification TSIdentification) {
        return null;

        // this.tsiImpl.getSender();

    }

    public static Receiver getReceiver(TSIdentification TSIdentification) {
        return null;
        //this.receiver = this.tsiImpl.getReceiver();
    }


}
