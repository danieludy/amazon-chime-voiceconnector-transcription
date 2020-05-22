package com.amazonaws.kvstranscribestreaming.streaming;

public class StreamingUtils {
    private static final String SIPREC_PREFIX = "SIPREC";
    public static boolean isSipRecRequest(final String callId) {
        return callId.startsWith(SIPREC_PREFIX);
    }
}