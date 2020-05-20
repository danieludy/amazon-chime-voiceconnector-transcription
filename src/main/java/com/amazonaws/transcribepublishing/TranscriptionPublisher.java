package com.amazonaws.transcribepublishing;

import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

public interface TranscriptionPublisher {
    void publish(TranscriptEvent e);
    void publishDone();
}
