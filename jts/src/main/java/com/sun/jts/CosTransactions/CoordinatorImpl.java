/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 1995-1997 IBM Corp. All rights reserved.
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

//----------------------------------------------------------------------------
//
// Module:      CoordinatorImpl.java
//
// Description: Transaction Coordinator base object implementation.
//
// Product:     com.sun.jts.CosTransactions
//
// Author:      Simon Holdsworth
//
// Date:        March, 1997
//----------------------------------------------------------------------------

package com.sun.jts.CosTransactions;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.INVALID_TRANSACTION;
import org.omg.CORBA.NVList;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.Request;
import org.omg.CORBA.SystemException;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.Coordinator;
import org.omg.CosTransactions.CoordinatorHelper;
import org.omg.CosTransactions.HeuristicHazard;
import org.omg.CosTransactions.HeuristicMixed;
import org.omg.CosTransactions.Inactive;
import org.omg.CosTransactions.NotPrepared;
import org.omg.CosTransactions.NotSubtransaction;
import org.omg.CosTransactions.PropagationContext;
import org.omg.CosTransactions.RecoveryCoordinator;
import org.omg.CosTransactions.Resource;
import org.omg.CosTransactions.Status;
import org.omg.CosTransactions.SubtransactionAwareResource;
import org.omg.CosTransactions.SubtransactionsUnavailable;
import org.omg.CosTransactions.Synchronization;
import org.omg.CosTransactions.SynchronizationUnavailable;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.Unavailable;
import org.omg.CosTransactions.Vote;
import org.omg.CosTransactions.otid_t;
import org.omg.PortableServer.POA;

import com.sun.jts.codegen.otsidl.JCoordinator;
import com.sun.jts.codegen.otsidl.JCoordinatorHelper;
import com.sun.jts.codegen.otsidl.JCoordinatorPOA;
import com.sun.jts.utils.LogFormatter;

/**
 * The CoordinatorImpl interface is an extension to the standard Coordinator interface that provides the common
 * operations for top-level transactions and subtransactions. It is used as a common superclass for the TopCoordinator
 * and SubCoordinator classes, and has no implementation.
 *
 * @version 0.01
 *
 * @author Simon Holdsworth, IBM Corporation
 *
 * @see
 */
//----------------------------------------------------------------------------
// CHANGE HISTORY
//
// Version By     Change Description
//   0.01  SAJH   Initial implementation.
//-----------------------------------------------------------------------------

abstract class CoordinatorImpl extends JCoordinatorPOA implements JCoordinator {

    /**
     * Reply action value indicating nothing should be done to the transaction.
     */
    static final int doNothing = 1;

    /**
     * Reply action value indicating that the transaction should be forgotten.
     */
    static final int forgetMe = 2;

    /**
     * Reply action value indicating that the transaction has active children.
     */
    static final int activeChildren = 3;

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract Status get_status();

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract Status get_parent_status();

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract Status get_top_level_status();

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean is_same_transaction(Coordinator other);

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean is_related_transaction(Coordinator other);

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean is_ancestor_transaction(Coordinator other);

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean is_descendant_transaction(Coordinator other);

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean is_top_level_transaction();

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract int hash_transaction();

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract int hash_top_level_tran();

    /*
     * Logger to log transaction messages
     */
    static Logger _logger = Logger.getLogger(CoordinatorImpl.class.getName());

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception Inactive The Coordinator is completing the transaction and cannot accept this registration.
     */
    @Override
    public abstract RecoveryCoordinator register_resource(Resource res) throws Inactive;

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception NotSubtransaction The Coordinator represents a top-level transaction and cannot accept the registration.
     * @exception Inactive The Coordinator is completing the transaction and cannot accept this registration.
     */
    @Override
    public abstract void register_subtran_aware(SubtransactionAwareResource sares) throws Inactive, NotSubtransaction;

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception Inactive The Coordinator is already completing the transaction.
     */
    @Override
    public abstract void rollback_only() throws Inactive;

    /**
     * OMG Coordinator operation required of all subclasses.
     */
    @Override
    public abstract String get_transaction_name();

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception Inactive The Coordinator is completing the transaction and cannot create a new child.
     * @exception SubtransactionsUnavailable Subtransactions are not available.
     */
    @Override
    public abstract Control create_subtransaction() throws Inactive, SubtransactionsUnavailable;

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception Inactive The Coordinator is completing the transaction and cannot accept the registration.
     * @exception SynchronizationUnavailable Synchronization is not supported.
     */
    @Override
    public abstract void register_synchronization(Synchronization sync) throws Inactive, SynchronizationUnavailable;

    /**
     * OMG Coordinator operation required of all subclasses.
     *
     * @exception Inactive The Coordinator is completing the transaction.
     */
    @Override
    public abstract PropagationContext get_txcontext() throws Unavailable;

    /**
     * IDL JCoordinator operation required of all subclasses.
     *
     * @exception Unavailable The global transaction identifier is not available.
     */
    @Override
    public abstract otid_t getGlobalTID();

    /**
     * IDL JCoordinator operation required of all subclasses.
     */
    @Override
    public abstract long getLocalTID();

    /**
     * IDL JCoordinator operation required of all subclasses.
     */
    @Override
    public abstract TransIdentity[] getAncestors();

    /**
     * IDL JCoordinator operation required of all subclasses.
     */
    @Override
    public abstract boolean isRollbackOnly();

    /**
     * Add the given Coordinator reference to the set of children of the target object.
     */
    abstract boolean addChild(CoordinatorImpl child);

    /**
     * Remove the given Coordinator reference from the set of children. If the target object is temporary, and no longer has
     * any children or registered resources, it destroys itself before returning.
     */
    abstract boolean removeChild(CoordinatorImpl child);

    /**
     * Direct the CoordinatorImpl to prepare to commit. If there are active subtransactions, the operation raises the
     * INVALID_TRANSACTION standard exception. Otherwise the CoordinatorImpl directs all registered Resources to prepare,
     * and returns the consolidated result to the caller. The CoordinatorImpl must guarantee that each Resource object
     * registered with it receives at most one prepare request (This includes the case where the Recoverable Server
     * registers the same Resource twice).
     *
     * @exception INVALID_TRANSACTION Indicates that the transaction may not be committed due to outstanding work.
     * @exception HeuristicMixed Indicates that a participant voted to roll the transaction back, but one or more others
     * have already heuristically committed.
     * @exception HeuristicHazard Indicates that a participant voted to roll the transaction back, but one or more others
     * may have already heuristically committed.
     */
    abstract Vote prepare() throws INVALID_TRANSACTION, HeuristicMixed, HeuristicHazard;

    /**
     * Direct the CoordinatorImpl to commit. The CoordinatorImpl directs all registered Resources to commit. Those Resources
     * that raised heuristic exceptions are subsequently told to forget the transaction. If there are no registered
     * Synchronization objects, and the CoordinatorImpl is not the root, it removes itself from the TransactionManager
     * associations and destroys itself before returning.
     *
     * @exception HeuristicMixed Indicates that heuristic decisions have been taken which have resulted in part of the
     * transaction being rolled back.
     * @exception HeuristicHazard Indicates that heuristic decisions may have been taken which have resulted in part of the
     * transaction being rolled back.
     * @exception NotPrepared Indicates that the transaction has not been prepared.
     */
    abstract void commit() throws HeuristicMixed, HeuristicHazard, NotPrepared;

    /**
     * Direct the CoordinatorImpl to commit in one phase if possible. If the coordinator has a single resource registered it
     * will flow the commit_one_phase mehtod to it, if it has >1 resource the method will return false indicating two phase
     * commit is required. Other than that the method behaves as commit.
     *
     * @exception HeuristicMixed Indicates that heuristic decisions have been taken which have resulted in part of the
     * transaction being rolled back.
     * @exception HeuristicHazard Indicates that heuristic decisions may have been taken which have resulted in part of the
     * transaction being rolled back.
     */
    abstract boolean commitOnePhase() throws HeuristicMixed, HeuristicHazard;

    /**
     * Direct the CoordinatorImpl to rollback the transaction. It directs every registered Resource to rollback. Those
     * Resources that raised heuristic exceptions are subsequently told to forget the transaction. If there are no
     * registered Synchronization objects, the CoordinatorImpl removes itself from the TransactionManager associations and
     * destroys itself before returning. If force is TRUE, the coordinator is rolled back even if this could compromise data
     * integrity (i.e. even if the coordinator is prepared). If force is FALSE, no attempt is made to roll back the
     * coordinator if it is prepared and FALSE is returned from the method.
     *
     * @exception HeuristicMixed Indicates that heuristic decisions have been taken which have resulted in part of the
     * transaction being rolled back.
     * @exception HeuristicHazard Indicates that heuristic decisions may have been taken which have resulted in part of the
     * transaction being rolled back.
     */
    abstract void rollback(boolean force) throws HeuristicMixed, HeuristicHazard;

    /**
     * Inform the CoordinatorImpl of an imminent reply. If the CoordinatorImpl has active children, it returns
     * activeChildren. If the CoordinatorImpl has already been registered, the operation returns doNothing. Otherwise the
     * CoordinatorImpl returns forgetMe, and the output parameter is set to the parent CoordinatorImpl, if any.
     */
    abstract CoordinatorImpl replyAction(int[/* 1 */] action) throws SystemException;

    /**
     * Inform the CoordinatorImpl that it is no longer a temporary construct. This is used when a request is received for an
     * CoordinatorImpl that was created as a temporary ancestor of a subtransaction.
     */
    abstract Long setPermanent();

    /**
     * Return a boolean value indicating whether the CoordinatorImpl is in an active state (i.e. not preparing or later
     * states).
     */
    abstract boolean isActive();

    /**
     * Return a boolean value indicating whether the CoordinatorImpl has registered with its superior (if any).
     */
    abstract boolean hasRegistered();

    /**
     * Record the object that is normally responsible for directing the CoordinatorImpl through termination. For a root
     * CoordinatorImpl, this is a CoordinatorTerm object; for a subordinate it is a CoordinatorResource.
     */
    abstract void setTerminator(CompletionHandler terminator);

    /**
     * Return the parent coordinator.
     */
    abstract Coordinator getParent();

    /**
     * Return the superior coordinator.
     */
    abstract Coordinator getSuperior();

    /**
     * Returns the object normally responsible for terminating the transaction.
     */
    abstract CompletionHandler getTerminator();

    /**
     * Cleans up an empty Coordinator object which was created temporarily for a read-only transactional request.
     */
    abstract void cleanUpEmpty(CoordinatorImpl forgetParent);

    /**
     * Clean up the state of the object.
     */
    public abstract void doFinalize();

    private static POA poa = null;
    private Coordinator thisRef = null;

    /**
     * Returns a CORBA object which represents the transaction.
     */
    synchronized final Coordinator object() {
        if (thisRef == null) {
            if (poa == null)
                poa = Configuration.getPOA("Coordinator"/* #Frozen */);

            try {
                poa.activate_object(this);
                thisRef = CoordinatorHelper.narrow(poa.servant_to_reference(this));
            } catch (Exception exc) {
                _logger.log(Level.SEVERE, "jts.create_coordinator_object_error", exc);
                String msg = LogFormatter.getLocalizedMessage(_logger, "jts.create_coordinator_object_error");
                throw new org.omg.CORBA.INTERNAL(msg);
            }
        }

        return thisRef;
    }

    /**
     * Returns the CoordinatorImpl which serves the given object.
     */
    synchronized static final CoordinatorImpl servant(Coordinator coord) {
        CoordinatorImpl result = null;

        if (coord instanceof CoordinatorImpl)
            result = (CoordinatorImpl) coord;
        else if (poa != null) {
            JCoordinator jcoord = JCoordinatorHelper.narrow(coord);
            if (jcoord != null)
                try {
                    result = (CoordinatorImpl) poa.reference_to_servant(jcoord);
                    if (result.thisRef == null)
                        result.thisRef = jcoord;
                } catch (Exception exc) {
                    _logger.log(Level.WARNING, "jts.cannot_locate_servant", "Coordinator");
                }
        }

        return result;
    }

    /**
     * Destroys the CoordinatorImpl object reference.
     */
    synchronized final void destroy() {
        if (poa != null && thisRef != null)
            try {
                poa.deactivate_object(poa.reference_to_id(thisRef));
                thisRef = null;
            } catch (Exception exc) {
                _logger.log(Level.WARNING, "jts.object_destroy_error", "Coordinator");
            }
    }

    /*
     * These methods are there to satisy the compiler. At some point when we move towards a tie based model, the
     * org.omg.Corba.Object interface method implementation below shall be discarded.
     */

    @Override
    public org.omg.CORBA.Object _duplicate() {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public void _release() {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public boolean _is_a(String repository_id) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public boolean _is_equivalent(org.omg.CORBA.Object that) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public boolean _non_existent() {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public int _hash(int maximum) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public Request _request(String operation) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public Request _create_request(Context ctx, String operation, NVList arg_list, NamedValue result) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public Request _create_request(Context ctx, String operation, NVList arg_list, NamedValue result, ExceptionList exceptions,
            ContextList contexts) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public org.omg.CORBA.Object _get_interface_def() {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public org.omg.CORBA.Policy _get_policy(int policy_type) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public org.omg.CORBA.DomainManager[] _get_domain_managers() {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }

    @Override
    public org.omg.CORBA.Object _set_policy_override(org.omg.CORBA.Policy[] policies, org.omg.CORBA.SetOverrideType set_add) {
        throw new org.omg.CORBA.NO_IMPLEMENT("This is a locally constrained object.");
    }
}
