package com.amazonaws.kvstranscribestreaming.streaming;

import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Responsible to ensure that reading from KVS is done until transactionId changes.
 *
 * <p>
 *     Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * </p>
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
public class KVSTransactionIdTagProcessor implements FragmentMetadataVisitor.MkvTagProcessor {

    private static final Logger log = LoggerFactory.getLogger(KVSTransactionIdTagProcessor.class);

    private final String transactionId;
    private boolean sameTransactionId;

    public KVSTransactionIdTagProcessor(String transactionId) {
        this.transactionId = transactionId;
        sameTransactionId = true;
    }

    public void process(MkvTag mkvTag, Optional<FragmentMetadata> currentFragmentMetadata) {
        if ("TransactionId".equals(mkvTag.getTagName())) {
            if (this.transactionId.equals(mkvTag.getTagValue())) {
                sameTransactionId = true;
            }
            else {
                log.info("TransactionId Id in tag does not match expected, will stop streaming. "
                        + "transactionId actual:" + mkvTag.getTagValue() + " expected: {}" + transactionId);
                sameTransactionId = false;
            }
        }
    }

    public boolean shouldStopProcessing() {
        return sameTransactionId == false;
    }
}