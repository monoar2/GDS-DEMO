package com.gds.queue;

/** Agency work queues, where PNRs wait for an agent's attention. */
public enum QueueName {
    /** Held and waiting to be ticketed before the deadline. */
    TICKETING,
    /** The ticketing deadline passed and the inventory was released. */
    EXPIRED,
    /** Ticketed PNRs cancelled with a refund to process. */
    REFUNDS,
    /** Placed by an agent for any other follow-up. */
    GENERAL
}
