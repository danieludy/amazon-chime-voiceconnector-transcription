package com.amazonaws.streamingeventmodel;

public interface StreamingStatusDetail {
    String getVoiceConnectorId();
    String getTransactionId();
    String getCallId();
    StreamingStatus getStreamingStatus();
    Direction getDirection();
    String getVersion();
}