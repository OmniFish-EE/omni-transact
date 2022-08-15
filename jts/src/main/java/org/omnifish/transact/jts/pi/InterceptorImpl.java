/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.omnifish.transact.jts.pi;

import static java.util.logging.Level.FINE;
import static org.omg.CORBA.CompletionStatus.COMPLETED_NO;
import static org.omg.CORBA.CompletionStatus.COMPLETED_YES;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Environment;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.INVALID_TRANSACTION;
import org.omg.CORBA.INV_POLICY;
import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.TRANSACTION_REQUIRED;
import org.omg.CORBA.TRANSACTION_ROLLEDBACK;
import org.omg.CORBA.TRANSACTION_UNAVAILABLE;
import org.omg.CORBA.TSIdentification;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.InputStream;
import org.omg.CosTSInteroperation.TAG_OTS_POLICY;
import org.omg.CosTSPortability.Receiver;
import org.omg.CosTSPortability.Sender;
import org.omg.CosTransactions.FORBIDS;
import org.omg.CosTransactions.OTSPolicy;
import org.omg.CosTransactions.OTSPolicyValueHelper;
import org.omg.CosTransactions.OTS_POLICY_TYPE;
import org.omg.CosTransactions.PropagationContext;
import org.omg.CosTransactions.PropagationContextHelper;
import org.omg.CosTransactions.PropagationContextHolder;
import org.omg.CosTransactions.REQUIRES;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.otid_t;
import org.omg.IOP.Codec;
import org.omg.IOP.ServiceContext;
import org.omg.IOP.TaggedComponent;
import org.omg.IOP.CodecPackage.FormatMismatch;
import org.omg.IOP.CodecPackage.InvalidTypeForEncoding;
import org.omg.IOP.CodecPackage.TypeMismatch;
import org.omg.PortableInterceptor.ClientRequestInfo;
import org.omg.PortableInterceptor.ClientRequestInterceptor;
import org.omg.PortableInterceptor.Current;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.ServerRequestInfo;
import org.omg.PortableInterceptor.ServerRequestInterceptor;
import org.omnifish.transact.api.api.Corba;
import org.omnifish.transact.jts.CosTransactions.CurrentTransaction;

/**
 * This is the implementation of the JTS PI-based client/server interceptor. This will be called during request/reply
 * invocation path.
 *
 * @author Ram Jeyaraman 11/11/2000, $Author: sankara $
 * @version 1.0, $Revision: 1.7 $ on $Date: 2007/04/02 09:07:59 $
 */
public class InterceptorImpl extends org.omg.CORBA.LocalObject implements ClientRequestInterceptor, ServerRequestInterceptor {

    private static final long serialVersionUID = 1L;

    /**
     * Logger to log transaction messages
     */
    static Logger _logger = Logger.getLogger(InterceptorImpl.class.getName());

    // class attributes

    private static final String name = "org.omnifish.transact.jts.pi.Interceptor";
    private static final int TransactionServiceId = 0;
    private static final SystemException SYS_EXC = new SystemException("", 0, CompletionStatus.COMPLETED_MAYBE) {
        private static final long serialVersionUID = 1L;
    };

    public static final int NO_REPLY_SLOT = 0;
    public static final int NULL_CTX_SLOT = 1;

    public static final Object PROPER_CTX = new Object();
    public static final Object NULL_CTX = new Object();

    public static final Object REPLY = new Object();
    public static final Object NO_REPLY = new Object();

    public static final String CLIENT_POLICY_CHECKING = "org.omnifish.transact.jts.pi.CLIENT_POLICY_CHECKING";
    public static final String INTEROP_MODE = "org.omnifish.transact.jts.pi.INTEROP_MODE";

    // The ReferenceFactoryManager from the orb.
    private static Object referenceFactoryManager;

    public static final ThreadLocal otsThreadLocal = new ThreadLocal<Object>() {
        @Override
        protected Object initialValue() {
            Object[] threadLocalState = new Object[2];
            threadLocalState[NO_REPLY_SLOT] = new ArrayListStack();
            threadLocalState[NULL_CTX_SLOT] = new ArrayListStack();
            return threadLocalState;
        }
    };

    private static PropagationContext nullContext, dummyContext;

    private static ORB txOrb;

    // instance attributes

    Current pic;
    Codec codec;
    int[] slotIds;
    TSIdentification tsIdentification;
    Sender sender;
    Receiver receiver;

    private boolean checkPolicy = true;
    private boolean interopMode = true;

    // constructor

    public InterceptorImpl(Current pic, Codec codec, int[] slotIds, TSIdentification tsi) {
        this.pic = pic;
        this.codec = codec;
        this.slotIds = slotIds;
        this.tsIdentification = tsi;

        if (this.tsIdentification != null) {
            sender = Corba.getSender(tsi);
            receiver = Corba.getReceiver(tsi);
        }

        // Check if client side checking is disabled. This allows client side
        // policy checking to be disabled (for testing purposes).
        checkPolicy = System.getProperty(CLIENT_POLICY_CHECKING, "true").equals("true");

        // Get the transaction interoperability mode.
        interopMode = System.getProperty(INTEROP_MODE, "true").equals("true");

        _logger.log(FINE, () -> "Transaction INTEROP Mode: " + interopMode);

    }

    // Proprietary hook for GlassFish. This is currently required since the
    // GlassFish does JTS initialization after the ORB.init() call.

    public void setTSIdentification(TSIdentification tsi) {
        if (tsi == null) {
            return;
        }

        this.tsIdentification = tsi;
        this.sender = Corba.getSender(tsi);
        this.receiver = Corba.getReceiver(tsi);
    }

    // implementation of the Interceptor interface.

    @Override
    public String name() {
        return InterceptorImpl.name;
    }

    @Override
    public void destroy() {
    }

    // Implementation of the ClientInterceptor interface.

    @Override
    public void send_request(ClientRequestInfo clientRequestInfo) throws ForwardRequest {
        // do IOR policy checking.
        TaggedComponent otsComp = null;
        try {
            otsComp = clientRequestInfo.get_effective_component(TAG_OTS_POLICY.value);
        } catch (BAD_PARAM e) {
            // ignore
        }

        short otsPolicyValue = -1;

        if (otsComp == null) {
            // In the case of J2EE RI, all published IORs must have an
            // associated OTS policy component. The only exception being the
            // location forwarded IORs returned by ORBD. Until a time, the ORBD
            // is capable of transcribing the target POA policies into the
            // location forwarded IOR, treat the absence of an OTS policy
            // component as being equivalent to ADAPTS. Once the ORBD is
            // able to support OTS policy components, the absence of an OTS
            // policy component must be treated as FORBIDS.
            otsPolicyValue = OTSPolicyImpl._ADAPTS.value();
        } else {
            TypeCode typeCode = txOrb.get_primitive_tc(TCKind.tk_short);
            Any any = null;
            try {
                any = codec.decode_value(otsComp.component_data, typeCode);
            } catch (TypeMismatch | FormatMismatch e) {
                throw new INTERNAL();
            }

            otsPolicyValue = OTSPolicyValueHelper.extract(any);
        }

        // TransactionService is not available.

        if (tsIdentification == null || sender == null) {
            if (otsPolicyValue == REQUIRES.value && this.checkPolicy) {
                throw new TRANSACTION_UNAVAILABLE();
            }
            return;
        }

        // TransactionService is available.

        // Check to see if there is a current transaction.
        boolean isTxAssociated = CurrentTransaction.isTxAssociated();
        if (!isTxAssociated) { // no tx context
            if (otsPolicyValue == REQUIRES.value && checkPolicy) {
                throw new TRANSACTION_REQUIRED();
            }
            return;
        }

        _logger.log(FINE, () ->
            " sending_request[" + clientRequestInfo.request_id() + "] : " + clientRequestInfo.operation() +
            ", ThreadName : " + Thread.currentThread().toString());

        // A current tx is available. Create service context.

        if (otsPolicyValue == FORBIDS.value && checkPolicy) {
            throw new INVALID_TRANSACTION();
        }

        PropagationContextHolder propagationContextHolder = new PropagationContextHolder();

        // If target object is co-located, no need to send tx context.
        // This optimization uses a dummy context to flag the local case, so
        // that the server receive point shall ignore the context (after doing
        // appropriate policy checking). The net gain is that the activation of
        // coordinator object is avoided.
        //
        // Note, this currently has issues with respect to checked behavior.
        // Currently, checked behaviour is disabled and shall be reinstated
        // once OTS RTF redrafts the OTS spec based on PI. An issue needs to be
        // filed.
        org.omg.CORBA.Object target = clientRequestInfo.effective_target();
        if (Corba.isProxy(target)) {
            // target is local
            // load a dummy context and discard the current tx context.
            propagationContextHolder.value = dummyContext;
        } else if (!this.interopMode) { // target is remote
            // load a null context and discard the current tx context.
            propagationContextHolder.value = nullContext;
        } else {
            sender.sending_request(clientRequestInfo.request_id(), propagationContextHolder);
        }

        // Add service context.
        clientRequestInfo.add_request_service_context(
            new ServiceContext(TransactionServiceId, encodePropagationContext(propagationContextHolder)),
            false);
    }

    @Override
    public void send_poll(ClientRequestInfo ri) {
        // do nothing.
    }

    @Override
    public void receive_reply(ClientRequestInfo clientRequestInfo) {
        // Check if a tx serviceContext context was received.
        ServiceContext serviceContext = getServiceContext(clientRequestInfo);
        if (serviceContext == null) {
            return;
        }

        // a tx serviceContext context is available.

        // Check if TransactionService is available.
        if (tsIdentification == null || sender == null) {
            throw new TRANSACTION_ROLLEDBACK(0, CompletionStatus.COMPLETED_YES);
        }

        _logger.log(FINE, () ->
            "   received_reply[" + clientRequestInfo.request_id() + "] : " + clientRequestInfo.operation() +
            ", ThreadName : "  + Thread.currentThread().toString());


        // Read the propagation context
        PropagationContext propagationContext = decodePropagationContext(serviceContext, COMPLETED_YES);


        // Set up the Environment instance with exception information.
        // The exception can be a SystemException or an UnknownUserException.

        Environment env = null;
        if (txOrb != null) {
            env = txOrb.create_environment();
        } else {
            // This shouldn't happen, but we'll be cautious
            env = ORB.init().create_environment();
        }

        env.exception(null);

        // call the OTS proprietary hook.

        try {
            sender.received_reply(clientRequestInfo.request_id(), propagationContext, env);
        } catch (org.omg.CORBA.WrongTransaction ex) {
            throw new INVALID_TRANSACTION(0, CompletionStatus.COMPLETED_YES);
        }
    }

    @Override
    public void receive_exception(ClientRequestInfo clientRequestInfo) throws ForwardRequest {
        // Check if a tx serviceContext context was received.
        ServiceContext serviceContext = null;
        try {
            serviceContext = getServiceContext(clientRequestInfo);
        } catch (Exception e) {
            return;
        }
        if (serviceContext == null) {
            return;
        }

        // a tx serviceContext context is available.

        // Set up the Environment instance with exception information.
        // The exception can be a SystemException or an UnknownUserException.
        Environment env = null;
        if (txOrb != null) {
            env = txOrb.create_environment();
        } else {
            // This shouldn't happen, but we'll be cautious
            env = ORB.init().create_environment();
        }

        SystemException exception = null;
        Any any = clientRequestInfo.received_exception();
        InputStream strm = any.create_input_stream();
        String repId = clientRequestInfo.received_exception_id();
        strm.read_string(); // read repId
        int minorCode = strm.read_long(); // read minorCode

        // Read completionStatus
        CompletionStatus completionStatus = CompletionStatus.from_int(strm.read_long());
        if (repId.indexOf("UNKNOWN") != -1) { // user exception ?
            if (minorCode == 1) { // read minorCode
                // user exception
            } else { // system exception
                exception = SYS_EXC;
            }
        } else { // system exception
            exception = SYS_EXC;
        }
        env.exception(exception);

        // Check if TransactionService is available.
        if (tsIdentification == null || sender == null) {
            throw new TRANSACTION_ROLLEDBACK(0, completionStatus);
        }

        // read the propagation context
        PropagationContext propagationContext = decodePropagationContext(serviceContext, completionStatus);

        // Call the OTS proprietary hook.

        try {
            sender.received_reply(clientRequestInfo.request_id(), propagationContext, env);
        } catch (org.omg.CORBA.WrongTransaction ex) {
            throw new INVALID_TRANSACTION(0, completionStatus);
        }
    }

    @Override
    public void receive_other(ClientRequestInfo clientRequestInfo) throws ForwardRequest {
        // Check if a tx serviceContext context was received.
        ServiceContext serviceContext = getServiceContext(clientRequestInfo);
        if (serviceContext == null) {
            return;
        }

        // a tx serviceContext context is available.

        // Check if TransactionService is available.
        if (tsIdentification == null || sender == null) {
            throw new TRANSACTION_ROLLEDBACK(0, COMPLETED_NO);
        }

        // Read the propagation context
        PropagationContext propagationContext = decodePropagationContext(serviceContext);

        // Set up the Environment instance with exception information.
        // The exception can be a SystemException or an UnknownUserException.

        Environment env = null;
        if (txOrb != null) {
            env = txOrb.create_environment();
        } else {
            // This shouldn't happen, but we'll be cautious
            env = ORB.init().create_environment();
        }

        env.exception(null);

        // call the OTS proprietary hook.

        try {
            sender.received_reply(clientRequestInfo.request_id(), propagationContext, env);
        } catch (org.omg.CORBA.WrongTransaction ex) {
            throw new INVALID_TRANSACTION(0, COMPLETED_NO);
        }
    }

    // implementation of the ServerInterceptor interface.

    @Override
    public void receive_request_service_contexts(ServerRequestInfo serverRequestInfo) throws ForwardRequest {

        // Since this could be called on a seperate thread, we need to
        // transfer the serviceContext context to the request PICurrent slots.
        // But for now, since we know that this is called by the same thread
        // as the target, we do not do it. But we should at some point.
        // do policy checking.

        OTSPolicy otsPolicy = null;
        try {
            otsPolicy = (OTSPolicy) serverRequestInfo.get_server_policy(OTS_POLICY_TYPE.value);
        } catch (INV_POLICY e) {
            // ignore. This will be treated as FORBIDS.
        }

        short otsPolicyValue = -1;

        if (otsPolicy == null) {
            // Once J2EE RI moves to POA based policy mechanism, default of
            // FORBIDS shall be used. Until then, we will use ADAPTS.
            // otsPolicyValue = OTSPolicyImpl._FORBIDS.value();
            otsPolicyValue = OTSPolicyImpl._ADAPTS.value();
        } else {
            otsPolicyValue = otsPolicy.value();
        }

        // get the tx contxt, if one was received.

        ServiceContext serviceContext = null;
        try {
            serviceContext = serverRequestInfo.get_request_service_context(TransactionServiceId);
        } catch (BAD_PARAM e) {
            // ignore, svc == null will be handled later.
        }

        // set threadLocal slot to indicate whether tx svc ctxt
        // was received or not. (svc == null) ==> NO_REPLY is true.

        if (serviceContext == null) {
            setThreadLocalData(NO_REPLY_SLOT, NO_REPLY);
        } else {
            setThreadLocalData(NO_REPLY_SLOT, REPLY);
        }

        // initially set the thread local slot to indicate proper ctxt.
        // if the null ctx is received, then it will be set later in the method.
        setThreadLocalData(NULL_CTX_SLOT, PROPER_CTX);

        try {

            // TransactionService is not available.

            if (tsIdentification == null || receiver == null) {
                if (serviceContext != null || otsPolicyValue == REQUIRES.value) {
                    throw new TRANSACTION_UNAVAILABLE();
                }
                return;
            }

            // TransactionService is available.

            // no tx context was received.
            if (serviceContext == null) {
                if (otsPolicyValue == REQUIRES.value) {
                    throw new TRANSACTION_REQUIRED();
                }
                return;
            }

            // a tx ctx was received.

            // check policy
            if (otsPolicyValue == FORBIDS.value) {
                throw new INVALID_TRANSACTION();
            }

            _logger.log(FINE, () ->
                " received_request[" + serverRequestInfo.request_id() + "] : " + serverRequestInfo.operation() +
                ", ThreadName : " + Thread.currentThread().toString());

            // Create service context.

            // sanity check.
            if (serviceContext.context_id != TransactionServiceId) {
                throw new INVALID_TRANSACTION();
            }

            PropagationContext propagationContext = decodePropagationContext(serviceContext);

            // check if a 'dummyContext' is present (local optimization).
            // If so, return.

            if (isDummyContext(propagationContext)) {
                // do nothing, since it is the same client thread which already
                // has the tx context association.
                // NOTE There is a chance that the 'nullContext' could be mistaken
                // to be a 'dummyContext', which may cause a valid 'nullContext'
                // to be ignored (!). But let's hope there won't be a collision :)

                // no need to send a reply ctx
                getThreadLocalData(NO_REPLY_SLOT); // pop item
                setThreadLocalData(NO_REPLY_SLOT, NO_REPLY); // push item
                return;
            }

            // check if a 'nullContext' was received,
            // and set the threadlocal data appropriately.

            if (isNullContext(propagationContext)) {
                // Indicate a null context
                getThreadLocalData(NULL_CTX_SLOT); // pop item
                setThreadLocalData(NULL_CTX_SLOT, NULL_CTX); // push item

                // No need to send a reply ctx
                getThreadLocalData(NO_REPLY_SLOT); // pop item
                setThreadLocalData(NO_REPLY_SLOT, NO_REPLY); // push item
                return;
            } else if (!this.interopMode) {
                getThreadLocalData(NULL_CTX_SLOT); // pop item
                setThreadLocalData(NULL_CTX_SLOT, NULL_CTX); // push item
            }

            // call the proprietary hook
            receiver.received_request(serverRequestInfo.request_id(), propagationContext);

        } catch (RuntimeException r) {
            // The server send point will not be called if the server receive
            // point raises an exception. So, do the cleanup.
            // ie., restore thread local data
            getThreadLocalData(NO_REPLY_SLOT);
            getThreadLocalData(NULL_CTX_SLOT);
            throw r;
        }
    }

    @Override
    public void receive_request(ServerRequestInfo ri) throws ForwardRequest {
        // do nothing.
    }

    private void processServerSendPoint(ServerRequestInfo serverRequestInfo, CompletionStatus completionStatus) {
        // Clear the null ctx indicator
        getThreadLocalData(NULL_CTX_SLOT);

        // See if a reply ctx needs to be sent.
        Object no_reply = getThreadLocalData(NO_REPLY_SLOT);
        if (no_reply == NO_REPLY) {
            return;
        }

        // TransactionService is not available.

        if (tsIdentification == null || receiver == null) {
            if (no_reply == REPLY) {
                // would the TransactionService go down during request
                // processing ? Maybe.
                throw new TRANSACTION_ROLLEDBACK(0, completionStatus);
            }
            return;
        }

        _logger.log(FINE, () ->
            "   sending_reply[" + serverRequestInfo.request_id() + "] : " + serverRequestInfo.operation() +
            ", ThreadName : " + Thread.currentThread().toString());

        // call the proprietary OTS interceptor.

        PropagationContextHolder propagationContextHolder = new PropagationContextHolder();
        receiver.sending_reply(serverRequestInfo.request_id(), propagationContextHolder);

        if (propagationContextHolder.value == null) {
            // No tx context available. This should not happen since a tx ctx was received.
            throw new TRANSACTION_ROLLEDBACK(0, completionStatus);
        }

        // Create the service context and set it in the reply.
        serverRequestInfo.add_reply_service_context(
            new ServiceContext(TransactionServiceId, encodePropagationContext(propagationContextHolder, completionStatus)),
            false);
    }

    @Override
    public void send_reply(ServerRequestInfo ri) {
        processServerSendPoint(ri, CompletionStatus.COMPLETED_YES);
    }

    @Override
    public void send_exception(ServerRequestInfo ri) throws ForwardRequest {
        Any any = ri.sending_exception();
        InputStream strm = any.create_input_stream();
        strm.read_string(); // repId
        strm.read_long(); // minorCode
        CompletionStatus completionStatus = CompletionStatus.from_int(strm.read_long());

        processServerSendPoint(ri, completionStatus);
    }

    @Override
    public void send_other(ServerRequestInfo ri) throws ForwardRequest {
        processServerSendPoint(ri, COMPLETED_NO);
    }


    // ### helper static methods.

    public static boolean isTxCtxtNull() {
        Object[] threadLocalState = (Object[]) otsThreadLocal.get();
        ArrayListStack stack = (ArrayListStack) threadLocalState[NULL_CTX_SLOT];
        return (stack.peek() == NULL_CTX);
    }

    public static boolean isNullContext(PropagationContext ctx) {
        return (ctx.current.coord == null && ctx.current.term == null);
    }

    public static boolean isDummyContext(PropagationContext ctx) {
        boolean proceed = false;
        try {
            proceed = ctx.implementation_specific_data.extract_boolean();
        } catch (BAD_OPERATION e) {
            return false;
        }
        return (proceed && isNullContext(ctx) && ctx.timeout == -1);
    }

    public static void setThreadLocalData(int slot, Object data) {
        Object[] threadLocalState = (Object[]) otsThreadLocal.get();
        ((ArrayListStack) threadLocalState[slot]).push(data);
    }

    public static Object getThreadLocalData(int slot) {
        Object[] threadLocalState = (Object[]) otsThreadLocal.get();
        return ((ArrayListStack) threadLocalState[slot]).pop();
    }

    public static void setOrb(ORB orb) {
        txOrb = orb;
        Any any = txOrb.create_any();
        any.insert_boolean(false);
        nullContext = new PropagationContext(0, new TransIdentity(null, null, new otid_t(0, 0, new byte[0])), new TransIdentity[0], any);

        any.insert_boolean(true);
        dummyContext = new PropagationContext(-1, new TransIdentity(null, null, new otid_t(-1, 0, new byte[0])), new TransIdentity[0], any);
        try {
            referenceFactoryManager = orb.resolve_initial_references("ReferenceFactoryManager");
        } catch (Exception ex) {
            _logger.log(Level.WARNING, ex.getMessage(), ex);
        }
    }

    public static boolean isEjbAdapterName(String[] adapterName) {
        boolean result = false;
        if (referenceFactoryManager != null) {
            try {
                result = (Boolean) referenceFactoryManager.getClass().getMethod("isRfmName", String[].class).invoke(referenceFactoryManager, (Object[]) adapterName);
            } catch (Exception ex) {
                _logger.log(Level.WARNING, ex.getMessage(), ex);
            }
        }

        return result;
    }

    private ServiceContext getServiceContext(ClientRequestInfo clientRequestInfo) {
        try {
            return clientRequestInfo.get_reply_service_context(TransactionServiceId);
        } catch (BAD_PARAM e) {
            return null;
            // do nothing (no tx service context in reply).
            // REVISIT If a valid tx context was sent, and none was received
            // back, then the checked transaction behaviour will cause the
            // transaction to fail.
        }
    }


    private byte[] encodePropagationContext(PropagationContextHolder propagationContextHolder) {
        return encodePropagationContext(propagationContextHolder, COMPLETED_NO);
    }

    private byte[] encodePropagationContext(PropagationContextHolder propagationContextHolder, CompletionStatus completionStatus) {
        Any any = txOrb.create_any();
        PropagationContextHelper.insert(any, propagationContextHolder.value);
        try {
            return codec.encode_value(any);
        } catch (InvalidTypeForEncoding e) {
            throw new INTERNAL(0, completionStatus);
        }
    }

    private PropagationContext decodePropagationContext(ServiceContext serviceContext) {
        return decodePropagationContext(serviceContext, COMPLETED_NO);
    }

    private PropagationContext decodePropagationContext(ServiceContext serviceContext, CompletionStatus completionStatus) {
        Any any = null;
        try {
            any = codec.decode_value(serviceContext.context_data, PropagationContextHelper.type());
        } catch (TypeMismatch | FormatMismatch  e) {
            throw new INTERNAL(0, completionStatus);
        }

        return PropagationContextHelper.extract(any);
    }

}
