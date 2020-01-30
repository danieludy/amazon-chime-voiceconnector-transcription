package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;

/**
 * TranscribedSegmentWriter writes the transcript segments to DynamoDB
 *
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
public class TranscribedSegmentWriter {

    private String contactId;
    private DynamoDB ddbClient;
    private Boolean consoleLogTranscriptFlag;
    private static final String TABLE_TRANSCRIPT = "TranscriptSegment";
    private static final Logger logger = LoggerFactory.getLogger(TranscribedSegmentWriter.class);

    public TranscribedSegmentWriter(String contactId, DynamoDB ddbClient, Boolean consoleLogTranscriptFlag) {

        this.contactId = Validate.notNull(contactId);
        this.ddbClient = Validate.notNull(ddbClient);
        this.consoleLogTranscriptFlag = Validate.notNull(consoleLogTranscriptFlag);
    }

    public String getContactId() {

        return this.contactId;
    }

    public DynamoDB getDdbClient() {

        return this.ddbClient;
    }

    public void writeToDynamoDB(TranscriptEvent transcriptEvent) {

        List<Result> results = transcriptEvent.transcript().results();
        if (results.size() > 0) {

            Result result = results.get(0);

            // we're only saving final transcripts here (note:  this will make the Ux appear slower)
            if (!result.isPartial()) {
                try {
                    Item ddbItem = toDynamoDbItem(result);
                    if (ddbItem != null) {
                        getDdbClient().getTable(TABLE_TRANSCRIPT).putItem(ddbItem);
                    }

                } catch (Exception e) {
                    logger.error("Exception while writing to DDB: ", e);
                }
            }
        }
    }

    /**
     * Transcribe website looks for Final event in DynamoDB payload to stop polling for messages. This is workaround
     * to display end of streaming.
     */
    public void writeTranscribeDoneToDynamoDB()
    {
        Instant now = Instant.now();
        logger.info("writing end of transcription to DDB for " + contactId);
        Item ddbItem = new Item()
            .withKeyComponent("CallId", contactId)
            .withKeyComponent("StartTime", now.getEpochSecond())
            .withString("Transcript", "END_OF_TRANSCRIPTION")
            // LoggedOn is an ISO-8601 string representation of when the entry was created
            .withString("LoggedOn", now.toString())
            .withBoolean("IsPartial", Boolean.FALSE)
            .withBoolean("IsFinal", Boolean.TRUE);
        
        if (ddbItem != null) {
            try {
                getDdbClient().getTable(TABLE_TRANSCRIPT).putItem(ddbItem);
            } catch (Exception e) {
                logger.error("Exception while writing to DDB:", e);
            }
        }
    }

    private Item toDynamoDbItem(Result result) {

        String contactId = this.getContactId();
        Item ddbItem = null;

        Instant now = Instant.now();

        if (result.alternatives().size() > 0) {
            if (!result.alternatives().get(0).transcript().isEmpty()) {

                ddbItem = new Item()
                        .withKeyComponent("CallId", contactId)
                        .withKeyComponent("StartTime", result.startTime())
                        .withDouble("EndTime", result.endTime())
                        .withString("SegmentId", result.resultId())
                        .withString("Transcript", result.alternatives().get(0).transcript())
                        // LoggedOn is an ISO-8601 string representation of when the entry was created
                        .withString("LoggedOn", now.toString())
                        .withBoolean("IsPartial", result.isPartial())
                        .withBoolean("IsFinal", Boolean.FALSE);

                if (consoleLogTranscriptFlag) {

                    NumberFormat nf = NumberFormat.getInstance();
                    nf.setMinimumFractionDigits(3);
                    nf.setMaximumFractionDigits(3);

                    logger.info(String.format("Thread %s %d: [%s, %s] - %s",
                            Thread.currentThread().getName(),
                            System.currentTimeMillis(),
                            nf.format(result.startTime()),
                            nf.format(result.endTime()),
                            result.alternatives().get(0).transcript()));
                }
            }
        }

        return ddbItem;
    }
}
