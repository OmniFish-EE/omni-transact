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

package ee.omnifish.transact.jts.utils.RecoveryHooks;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.util.Hashtable;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.omg.CosTransactions.Coordinator;

import ee.omnifish.transact.jts.CosTransactions.ControlImpl;
import ee.omnifish.transact.jts.CosTransactions.CurrentTransaction;
import ee.omnifish.transact.jts.CosTransactions.GlobalTID;
import ee.omnifish.transact.jts.codegen.otsidl.JCoordinator;
import ee.omnifish.transact.jts.codegen.otsidl.JCoordinatorHelper;
import ee.omnifish.transact.jts.jtsxa.Utility;

/**
 * This class defines APIs for the test harness to induce recovery by setting crash points (in the case of TM crashes)
 * and wait points (in the case of RM crashes).
 *
 * In order to induce TM crashes, the test harness sets the failure points by calling setCrashPoint(), after
 * transaction.begin() from each thread. Thus, multiple threads involving different transactions can set different
 * points of failure, which will cause the TM to wait at the failure points, until crash() method is invoked. Note: Only
 * those transactions calling setCrashPoint() will be affected.
 *
 * The crash() method needs to be invoked from a seperate thread. The TM would increment TestRecovery.waitCount field to
 * indicate the total number of failure points reached. When the waitCount reaches the expected value (obtained through
 * getWaitCount()), the test harness shall call crash().
 *
 * In order to test RM recovery, the test harness sets the failure points by calling setWaitPoint(), after
 * transaction.begin() from each thread. The TM waits at predefined failure points for the stipulated time duration,
 * during which the RM is expected to crash; after which the TM will proceed with regular completion (a human is
 * expected to crash the RM manually during the wait duration). As in the TM recovery case, the waitCount will be
 * incremented to indicate the total number of failure points reached.
 *
 * If the RM does not crash during the stipulated time duration, the TM will proceed with normal completion for the
 * specific transaction. It does not matter if the RM comes back alive, since the TM would anyway retry completion.
 * Note: Only those transactions calling setWaitPoint() will be affected.
 *
 * @author Ram Jeyaraman 04/21/1999
 */
public class FailureInducer {

    /*
     * Logger to log transaction messages
     */
    static Logger _logger = Logger.getLogger(FailureInducer.class.getName());

    // static finals
    public static final int ACTIVE = 0;
    public static final int PREPARING = 1;
    public static final int PREPARED = 2;
    public static final int COMPLETING = 3;
    public static final int COMPLETED = 4;

    // static fields

    private static boolean failureInducerIsActive;
    private static boolean crash;
    private static int waitPeriod = 0;
    private static int waitCount = 0;
    private static int recoveryWaitDuration = 0;
    private static Hashtable crashList = new Hashtable();
    private static Hashtable waitList = new Hashtable();
    private static Hashtable waitTime = new Hashtable();
    private static ResourceBundle messages = ResourceBundle.getBundle("ee.omnifish.transact.jts.utils.RecoveryHooks.Messages"/* #Frozen */);


    // static methods

    /**
     * This activates the FailureInducer. An application needs to activate the failure inducer first, before setting the
     * fail or wait points.
     */
    public static void activateFailureInducer() {
        failureInducerIsActive = true;
    }

    /**
     * This deactivates the FailureInducer. An application deactivate the failure inducer, to temporarily stop failure
     * inducement. The fail or wait points are not forgotten during the dormant state.
     */
    public static void deactivateFailureInducer() {
        failureInducerIsActive = false;
    }

    /**
     * @return the current state (active or inactive) of failure inducer.
     */
    public static boolean isFailureInducerActive() {
        return failureInducerIsActive;
    }

    /**
     * Setting a crash point will cause the TM to wait at the failure point, until crash() is called.
     *
     * @param crashPoint pre-defined failure points (PREPARING, PREPARED, COMMITTING, COMMITTED).
     */
    public static void setCrashPoint(Integer crashPoint) {
        // sanity check
        if (crashPoint == null) {
            _logger.log(SEVERE, "jts.invalid_crash_point");
            return;
        }

        GlobalTID gtid = getGlobalTID();
        if (gtid != null) {
            crashList.put(gtid, crashPoint);
        }
    }

    /**
     * Setting a wait point will cause the TM to wait at the failure point, for the stipulated wait duration.
     *
     * @param waitPoint pre-defined failure points (PREPARING, PREPARED, COMMITTING, COMMITTED).
     * @param waitDuration time duration (seconds) for RM failure to happen.
     */
    public static void setWaitPoint(Integer waitPoint, int waitDuration) {
        // sanity check
        if (waitPoint == null) {
            _logger.log(SEVERE, "jts.invalid_wait_point");
            return;
        }

        GlobalTID gtid = getGlobalTID();
        if (gtid != null) {
            waitList.put(gtid, waitPoint);
            waitTime.put(gtid, waitDuration);
        }
    }

    /**
     * Forces the TM to crash.
     */
    public static void crash() {
        crash = true;
    }

    /**
     * Increments the wait count (called only by TM).
     */
    private static void incrementWaitCount() {
        waitCount++;
    }

    /**
     * @return the total number of failure points reached.
     */
    public static int getWaitCount() {
        return waitCount;
    }

    /**
     * This method is called by the coordinator at every valid failure point. If the crash point or the wait point set for
     * the current transaction matches the current failure point, an appropriate action (crash or wait) is taken.
     * <em>Note:</em> Crash action takes precedence over wait actions for the same failure point.
     *
     * @param gtid the coordinator object (which represents the transaction.
     * @param failPoint indicates the current failure point in coordinator code.
     */
    public static void waitForFailure(GlobalTID gtid, Integer failPoint) {
        // Sanity check
        if (gtid == null) {
            return;
        }

        Integer crashPoint = (Integer) crashList.get(gtid);
        Integer waitPoint = (Integer) waitList.get(gtid);

        // No crash point or wait point has been set for the transaction
        if (crashPoint == null && waitPoint == null) {
            return;
        }

        _logger.log(WARNING, "jts.failpoint", failPoint);
        // Increment wait count and wait for the crash flag to be set
        if (crashPoint != null && crashPoint.equals(failPoint)) {
            incrementWaitCount();
            while (!crash) {
                try {
                    Thread.sleep(3000);
                } catch (Exception e) {
                }
            }
            System.exit(0);
        }

        // wait for the wait duration and continue
        if (waitPoint != null && waitPoint.equals(failPoint)) {
            Integer waitDuration = (Integer) waitTime.get(gtid);

            // wait duration has not be set or is invalid
            if (waitDuration == null || waitDuration.intValue() < 0) {
                return;
            }

            // wait for the stipulated duration
            try {
                Thread.sleep(waitDuration.intValue() * 1000L);
            } catch (Exception e) {
            }
        }
    }

    /**
     * Enable wait action dyring delegated recovery via "add-wait-point-during-recovery" property added to the
     * transaction-service config
     */
    public static void setWaitPointRecovery(int waitDuration) {
        recoveryWaitDuration = waitDuration;
    }

    /**
     * Perform wait action dyring delegated recovery
     */
    public static void waitInRecovery() {
        if (recoveryWaitDuration > 0) {
            _logger.log(WARNING, "jts.failpoint", "RECOVERY");
            // wait for the stipulated duration
            try {
                Thread.sleep(recoveryWaitDuration * 1000L);
            } catch (Exception e) {
            }
        }
    }

    private static GlobalTID getGlobalTID() {
        GlobalTID gtid = null;
        Coordinator coord = Utility.getCoordinator(Utility.getControl());
        JCoordinator jcoord = JCoordinatorHelper.narrow(coord);
        if (jcoord != null) {
            gtid = new GlobalTID(jcoord.getGlobalTID());
        } else {
            ControlImpl control = CurrentTransaction.getCurrent();
            if (control != null) {
                gtid = control.getGlobalTID();
            }
        }

        return gtid;
    }
}
