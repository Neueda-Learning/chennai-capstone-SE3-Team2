package com.yellow.trade.security;

import org.springframework.stereotype.Component;

@Component
public class TokenAccountContext {

    public Long currentAccountId() {
        return 1L;
    }
}