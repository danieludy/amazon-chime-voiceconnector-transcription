package com.amazonaws.kvstranscribestreaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Map;

/**
 * <p>
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * </p>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscribeStreamingContext {
    // Keep the property name consistent with the name in streaming event detail.
    @NonNull private String streamArn;
    @NonNull private String startFragmentNumber;
    @NonNull private String transactionId;
    @NonNull private String callId;
    @NonNull private String streamingStatus;
    @NonNull private String startTime;
    @NonNull private TranscriptionPlatform platform;
    @NonNull private String voiceConnectorId;
    @NonNull private String direction;
    @NonNull private String mediaType;
    private Map<String, String> inviteHeaders;
    private String fromNumber;
    private String toNumber;
    private Boolean isCaller;
}