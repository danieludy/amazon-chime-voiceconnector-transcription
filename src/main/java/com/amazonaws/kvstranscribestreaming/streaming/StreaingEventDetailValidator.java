package com.amazonaws.kvstranscribestreaming.streaming;

import com.amazonaws.kvstranscribestreaming.streaming.exception.StreamingStatusValidationException;
import com.amazonaws.streamingeventmodel.StreamingStatusDetail;
import com.amazonaws.streamingeventmodel.StreamingStatusStartedDetail;
import org.apache.commons.lang3.StringUtils;

public class StreaingEventDetailValidator {

    public static void validateStreamingStartedEvent(final StreamingStatusStartedDetail streamingStatusStartedDetail) {
        validateStreamingEvent(streamingStatusStartedDetail);

        if (StringUtils.isBlank(streamingStatusStartedDetail.getStartTime())) {
            throw new StreamingStatusValidationException("streaming start time is empty");
        }
        if (streamingStatusStartedDetail.getMediaType() == null) {
            throw new StreamingStatusValidationException("Media type is not present");
        }
        if (StringUtils.isBlank(streamingStatusStartedDetail.getStartFragmentNumber())) {
            throw new StreamingStatusValidationException("Fragment number is  empty");
        }
        if (StringUtils.isBlank(streamingStatusStartedDetail.getStreamArn())) {
            throw new StreamingStatusValidationException("Stream ARN is empty");
        }
        if (StreamingUtils.isSipRecRequest(streamingStatusStartedDetail.getCallId())) {
            if (streamingStatusStartedDetail.getInviteHeaders() == null ||
                    streamingStatusStartedDetail.getInviteHeaders().isEmpty()) {
                throw new StreamingStatusValidationException("InviteHeaders are not present");
            }
            if (StringUtils.isBlank(streamingStatusStartedDetail.getSiprecMetadata())) {
                throw new StreamingStatusValidationException("SipRecMetadata is empty");
            }
        }
    }

    private static void validateStreamingEvent(StreamingStatusDetail streamingStatusDetail) {
        if (StringUtils.isBlank(streamingStatusDetail.getVoiceConnectorId())) {
            throw new StreamingStatusValidationException("VoiceConnectorId is empty");
        }
        if (StringUtils.isBlank(streamingStatusDetail.getTransactionId())) {
            throw new StreamingStatusValidationException("TransactionId is empty");
        }
        if (StringUtils.isBlank(streamingStatusDetail.getCallId())) {
            throw new StreamingStatusValidationException("CallId is empty");
        }
        if (streamingStatusDetail.getDirection() == null) {
            throw new StreamingStatusValidationException("call direction is not present");
        }
        if (!streamingStatusDetail.getVersion().equals("0")) {
            throw new StreamingStatusValidationException("Invalid version");
        }
    }
}
