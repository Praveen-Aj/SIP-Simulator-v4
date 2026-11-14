/*
 *  SIP Inspector - Call State Machine
 *  RFC 3261 Session State Management
 */
package sipinspector.Media;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CallState represents the operational states of a SIP session / dialog
 * adhering to RFC 3261 and RFC 3960 (Early Media).
 * 
 * Includes explicit valid state transitions to guarantee protocol integrity.
 */
public enum CallState {
    IDLE,
    CALLING,
    PROCEEDING,
    RINGING,
    EARLY_MEDIA,
    ESTABLISHED,
    TERMINATING,
    TERMINATED,
    FAILED;

    private static final Map<CallState, Set<CallState>> VALID_TRANSITIONS;

    static {
        Map<CallState, Set<CallState>> transitions = new HashMap<>();

        // IDLE -> Initial states
        transitions.put(IDLE, EnumSet.of(CALLING, PROCEEDING, RINGING, ESTABLISHED, FAILED));

        // CALLING (INVITE sent) -> Provisional or Final
        transitions.put(CALLING, EnumSet.of(PROCEEDING, RINGING, EARLY_MEDIA, ESTABLISHED, TERMINATING, TERMINATED, FAILED));

        // PROCEEDING (100 Trying received) -> Ringing, Early Media, Established, or Failure
        transitions.put(PROCEEDING, EnumSet.of(RINGING, EARLY_MEDIA, ESTABLISHED, TERMINATING, TERMINATED, FAILED));

        // RINGING (180 Ringing) -> Early Media, Established, Cancel/Terminate, or Failure
        transitions.put(RINGING, EnumSet.of(EARLY_MEDIA, ESTABLISHED, TERMINATING, TERMINATED, FAILED));

        // EARLY_MEDIA (183 Session Progress with SDP) -> Ringing, Established, Terminating, or Failure
        transitions.put(EARLY_MEDIA, EnumSet.of(RINGING, ESTABLISHED, TERMINATING, TERMINATED, FAILED));

        // ESTABLISHED (200 OK + ACK exchanged) -> Terminating (BYE sent/rcvd), Terminated, or Failed
        transitions.put(ESTABLISHED, EnumSet.of(TERMINATING, TERMINATED, FAILED));

        // TERMINATING (BYE in-flight) -> Terminated or Failed
        transitions.put(TERMINATING, EnumSet.of(TERMINATED, FAILED));

        // Terminal states
        transitions.put(TERMINATED, EnumSet.of(IDLE));
        transitions.put(FAILED, EnumSet.of(IDLE));

        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /**
     * Checks if transitioning from this state to the next state is valid according to RFC 3261.
     */
    public boolean canTransitionTo(CallState nextState) {
        if (nextState == null) {
            return false;
        }
        if (this == nextState) {
            return true; // Idempotent stay in state
        }
        Set<CallState> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(nextState);
    }

    /**
     * Helper to validate a transition between any two states.
     */
    public static boolean isValidTransition(CallState from, CallState to) {
        if (from == null || to == null) return false;
        return from.canTransitionTo(to);
    }

    /**
     * Returns true if the state represents an active dialog where media may flow.
     */
    public boolean isActive() {
        return this == EARLY_MEDIA || this == ESTABLISHED;
    }

    /**
     * Returns true if the state is terminal (dialog completed or aborted).
     */
    public boolean isTerminal() {
        return this == TERMINATED || this == FAILED;
    }
}
