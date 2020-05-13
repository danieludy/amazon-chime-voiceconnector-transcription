package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.streamingeventmodel.Platform;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrate Amazon VoiceConnectors's real-time transcription feature using
 * AWS Kinesis Video Streams and AWS Transcribe. The data flow is :
 * <p>
 * Amazon CloudWatch Events => Amazon SQS => AWS Lambda => AWS Transcribe => AWS
 * DynamoDB & S3
 *
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
public class KVSTranscribeStreamingLambda implements RequestHandler<SQSEvent, String> {
    private static final Logger logger = LoggerFactory.getLogger(KVSTranscribeStreamingLambda.class);
    private static final String LAMBDA_KEY_PREFIX = "KVSTranscribeStreamingLambda:";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        try {
            logger.info(LAMBDA_KEY_PREFIX + " received request : " + objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            logger.error(LAMBDA_KEY_PREFIX + " Error happened where serializing the event", e);
        }
        logger.info(LAMBDA_KEY_PREFIX + " received context: " + context.toString());

        try {
            for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
                KVSTranscribeStreamingHandler handler = new KVSTranscribeStreamingHandler(Platform.LAMBDA);

                logger.info("body from sqs message {}", sqsMessage.getBody());
                handler.handleRequest(sqsMessage.getBody());
            }
        } catch (Exception e) {
            logger.error(LAMBDA_KEY_PREFIX + " KVS to Transcribe Streaming failed with: ", e);
            return "{ \"result\": \"Failed\" }";
        }
        return "{ \"result\": \"Success\" }";
    }
}
