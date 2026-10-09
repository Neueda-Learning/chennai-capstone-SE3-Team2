package com.yellow.executor.settle;

/** version has been lost more times than the retry budget allows. */
public class LockExhaustedException extends RuntimeException {

    public LockExhaustedException(String message) {
        super(message);
    }
}
