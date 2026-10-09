package com.yellow.trade.preferences.api;

/**
 * How the platform reaches a customer: the one seam the preferences module
 * publishes (Sprint 10, decision log 0002). The notifications module calls it
 * on every send, in process. It is not an HTTP route, so no customer token
 * can reach it.
 *
 * Never answers null or empty for an account that exists: with nothing
 * stored, the documented default is EMAIL to the address on the customer's
 * profile, with {@code fromDefault} true (decision log 0004); a customer with
 * no address on file resolves to IN_APP. An account that does not exist is
 * {@link UnknownAccountException}.
 *
 * The contact detail is read from the profile at the moment of resolving,
 * never copied into the preferences module (decision log 0003).
 */
public interface ChannelResolver {

    ResolvedChannel resolve(long accountId);

    /** No account has that key. */
    final class UnknownAccountException extends RuntimeException {
        public UnknownAccountException(long accountId) {
            super("no account " + accountId);
        }
    }
}
