package com.amazonaws.streamingeventmodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Map;

/*
 * <p>Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.</p>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder(alphabetic=true)
public class StreamingStatusStartedDetail implements StreamingStatusDetail {

    private static final String currentVersion = "0";

    @NonNull
    private String voiceConnectorId;
    @NonNull
    private String transactionId;
    @NonNull
    private String callId;
    @NonNull
    private Direction direction;
    @NonNull
    private String startTime;
    @NonNull
    private MediaType mediaType;
    @NonNull
    private String startFragmentNumber;
    @NonNull
    private String streamArn;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> inviteHeaders;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String siprecMetadata;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Sdp sdp;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String fromNumber;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String toNumber;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isCaller;

    @Override
    public StreamingStatus getStreamingStatus() {
        return StreamingStatus.STARTED;
    }

    @Override
    public String getVersion() {
        return currentVersion;
    }
}
