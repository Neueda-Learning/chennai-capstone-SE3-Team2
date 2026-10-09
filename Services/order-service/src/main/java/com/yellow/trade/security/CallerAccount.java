package com.yellow.trade.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class CallerAccount {

    static final String REQUEST_ATTRIBUTE = "com.yellow.trade.verifiedAccountId";

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

    public boolean canReach(Long addressedAccountId) {
        return addressedAccountId != null && addressedAccountId == accountId();
    }
}
