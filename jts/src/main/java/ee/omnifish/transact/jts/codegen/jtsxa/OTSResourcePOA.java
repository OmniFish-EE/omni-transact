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

package ee.omnifish.transact.jts.codegen.jtsxa;

import static org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE;

import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.InvokeHandler;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.ResponseHandler;
import org.omg.PortableServer.Servant;

/**
 * com/sun/jts/codegen/jtsxa/OTSResourcePOA.java . Generated by the IDL-to-Java compiler (portable), version "3.1" from
 * com/sun/jts/jtsxa.idl Tuesday, February 5, 2002 12:57:25 PM PST
 */

//#-----------------------------------------------------------------------------
public abstract class OTSResourcePOA extends Servant implements OTSResourceOperations, InvokeHandler {

    // Constructors

    private static java.util.Hashtable _methods = new java.util.Hashtable();
    static {
        _methods.put("getGlobalTID", 0);
        _methods.put("prepare", 1);
        _methods.put("rollback", 2);
        _methods.put("commit", 3);
        _methods.put("commit_one_phase", 4);
        _methods.put("forget", 5);
    }

    @Override
    public OutputStream _invoke(String $method, InputStream in, ResponseHandler $rh) {
        OutputStream out = null;
        Integer __method = (Integer) _methods.get($method);
        if (__method == null)
            throw new BAD_OPERATION(0, COMPLETED_MAYBE);

        switch (__method.intValue()) {
        case 0: // jtsxa/OTSResource/getGlobalTID
        {
            org.omg.CosTransactions.otid_t $result = null;
            $result = this.getGlobalTID();
            out = $rh.createReply();
            org.omg.CosTransactions.otid_tHelper.write(out, $result);
            break;
        }

        case 1: // CosTransactions/Resource/prepare
        {
            try {
                org.omg.CosTransactions.Vote $result = null;
                $result = this.prepare();
                out = $rh.createReply();
                org.omg.CosTransactions.VoteHelper.write(out, $result);
            } catch (org.omg.CosTransactions.HeuristicMixed $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicMixedHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicHazard $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicHazardHelper.write(out, $ex);
            }
            break;
        }

        case 2: // CosTransactions/Resource/rollback
        {
            try {
                this.rollback();
                out = $rh.createReply();
            } catch (org.omg.CosTransactions.HeuristicCommit $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicCommitHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicMixed $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicMixedHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicHazard $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicHazardHelper.write(out, $ex);
            }
            break;
        }

        case 3: // CosTransactions/Resource/commit
        {
            try {
                this.commit();
                out = $rh.createReply();
            } catch (org.omg.CosTransactions.NotPrepared $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.NotPreparedHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicRollback $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicRollbackHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicMixed $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicMixedHelper.write(out, $ex);
            } catch (org.omg.CosTransactions.HeuristicHazard $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicHazardHelper.write(out, $ex);
            }
            break;
        }

        case 4: // CosTransactions/Resource/commit_one_phase
        {
            try {
                this.commit_one_phase();
                out = $rh.createReply();
            } catch (org.omg.CosTransactions.HeuristicHazard $ex) {
                out = $rh.createExceptionReply();
                org.omg.CosTransactions.HeuristicHazardHelper.write(out, $ex);
            }
            break;
        }

        case 5: // CosTransactions/Resource/forget
        {
            this.forget();
            out = $rh.createReply();
            break;
        }

        default:
            throw new BAD_OPERATION(0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
        }

        return out;
    } // _invoke

    // Type-specific CORBA::Object operations
    private static String[] __ids = { "IDL:jtsxa/OTSResource:1.0", "IDL:omg.org/CosTransactions/Resource:1.0" };

    @Override
    public String[] _all_interfaces(org.omg.PortableServer.POA poa, byte[] objectId) {
        return __ids.clone();
    }

    public OTSResource _this() {
        return OTSResourceHelper.narrow(super._this_object());
    }

    public OTSResource _this(org.omg.CORBA.ORB orb) {
        return OTSResourceHelper.narrow(super._this_object(orb));
    }

} // class OTSResourcePOA