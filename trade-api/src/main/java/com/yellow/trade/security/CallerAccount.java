package com.yellow.trade.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Who the current caller is, according to a token that has already been
 * verified.
 *
 * The service layer asks this rather than being handed an HttpServletRequest,
 * which is what keeps a servlet type out of a service method signature. The
 * filter puts the verified account key on the request; this reads it back.
 *
 * Note what this class does NOT do: it never verifies anything. If a value is
 * here, JwtTokenVerifier already accepted the token it came from. Answering
 * "is this caller authenticated" happens once, in the filter, for every route
 * under the protected prefix.
 */
@Component
public class CallerAccount {

    static final String REQUEST_ATTRIBUTE = "com.yellow.trade.verifiedAccountId";

    /**
     * @return the numeric account key the caller's token was issued for
     * @throws IllegalStateException if called outside a request that passed
     *         the token filter, which is a wiring bug rather than a bad
     *         request -- it means a route was exposed without protection
     */
    public long accountId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null
                ? null
                : attributes.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

        if (value == null) {
            throw new IllegalStateException(
                    "no verified caller on this request: a route outside the protected prefix "
                            + "asked who the caller is");
        }
        return (Long) value;
    }

    /**
     * Whether the caller's token reaches the account being addressed.
     *
     * The token proves who you are, not what you may touch. The two questions
     * are answered in different places on purpose: validity in the filter,
     * before any controller runs; reachability here, where the account key is
     * actually known.
     */
    public boolean canReach(Long addressedAccountId) {
        return addressedAccountId != null && addressedAccountId == accountId();
    }
}
