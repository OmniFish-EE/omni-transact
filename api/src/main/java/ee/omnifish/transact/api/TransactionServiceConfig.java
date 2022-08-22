package ee.omnifish.transact.api;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface TransactionServiceConfig {

    /**
     * Gets the value of the automaticRecovery property.
     *
     * If true, server instance attempts recovery at restart
     *
     * @return possible object is
     *         {@link String }
     */
    String getAutomaticRecovery();

    /**
     * Sets the value of the automaticRecovery property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setAutomaticRecovery(String value) throws PropertyVetoException;

    /**
     * Gets the value of the timeoutInSeconds property.
     *
     * amount of time the transaction manager waits for response from a
     * datasource participating in transaction.
     * A value of 0 implies infinite timeout
     *
     * @return possible object is
     *         {@link String }
     */
    String getTimeoutInSeconds();

    /**
     * Sets the value of the timeoutInSeconds property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setTimeoutInSeconds(String value) throws PropertyVetoException;

    /**
     * Gets the value of the txLogDir property.
     *
     * Transaction service creates a sub directory 'tx' under tx-log-dir to
     * store the transaction logs. The default value of the tx-log-dir is
     * $INSTANCE-ROOT/logs. If this attribute is not explicitly specified in the
     * <transaction-service> element, 'tx' sub directory will be created under
     * the path specified in log-root attribute of <domain> element.
     *
     * @return possible object is
     *         {@link String }
     */
    String getTxLogDir();

    /**
     * Sets the value of the txLogDir property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setTxLogDir(String value) throws PropertyVetoException;

    /**
     * Gets the value of the heuristicDecision property.
     *
     * During recovery, if outcome of a transaction cannot be determined from
     * the logs, then this property is used to fix the outcome
     *
     * @return possible object is
     *         {@link String }
     */
    String getHeuristicDecision();

    /**
     * Sets the value of the heuristicDecision property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setHeuristicDecision(String value) throws PropertyVetoException;

    /**
     * Gets the value of the retryTimeoutInSeconds property.
     *
     * Used to determine the retry time in the following scenarios.
     *
     * 1 Time to wait at the transaction recovery time, when resources are
     *   unreachable.
     * 2 If there are any transient exceptions in the second phase of the
     *   two PC protocol.
     *
     * A negative value indicates infinite retry. '0' indicates no retry.
     * A positive value indicates the number of seconds for which retry will be
     * attempted. Default is 10 minutes which may be appropriate for a database
     * being restarted
     *
     * @return possible object is
     *         {@link String }
     */
    String getRetryTimeoutInSeconds();

    /**
     * Sets the value of the retryTimeoutInSeconds property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setRetryTimeoutInSeconds(String value) throws PropertyVetoException;

    /**
     * Gets the value of the keypointInterval property.
     *
     * property used to specify the number of transactions between keypoint
     * operations on the log. A Keypoint operations could reduce the size of the
     * transaction log files. A larger value for this property
     * (for example, 1000) will result in larger transaction log files,
     * between log compactions, but less keypoint operations, and potentially
     * better performance. A smaller value (e.g. 20) results in smaller log
     * files but slightly reduced performance due to the greater frequency of
     * keypoint operations.
     *
     * @return possible object is
     *         {@link String }
     */
    String getKeypointInterval();

    /**
     * Sets the value of the keypointInterval property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    void setKeypointInterval(String value) throws PropertyVetoException;

    String getPropertyValue(String key);

    default List<Map.Entry<String, String>> getProperties() {
        return new ArrayList<>(Map.of(
            "oracle-xa-recovery-workaround", "true",
            "disable-distributed-transaction-logging", "false",
            "xaresource-txn-timeout", "120",
            "pending-txn-cleanup-interval", "60",
            "use-last-agent-optimization", "true",
            "wait-time-before-recovery-insec", "60",
            "db-logging-resource", "java:comp/DefaultDataSource",
            "orb-port", "0"
        ).entrySet());
    }

}
