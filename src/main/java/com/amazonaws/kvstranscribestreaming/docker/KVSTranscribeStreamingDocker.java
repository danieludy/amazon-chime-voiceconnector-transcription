package com.amazonaws.kvstranscribestreaming.docker;

import com.amazonaws.kvstranscribestreaming.constants.Platform;
import com.amazonaws.kvstranscribestreaming.handler.KVSTranscribeStreamingHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
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
public class KVSTranscribeStreamingDocker {
    private static final Logger logger = LoggerFactory.getLogger(KVSTranscribeStreamingDocker.class);
    private static final String DOCKER_KEY_PREFIX = "KVSTranscribeStreamingDocker:";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void main(String[] args) {
        final KVSTranscribeStreamingHandler handler = new KVSTranscribeStreamingHandler(Platform.ECS);
        String eventBody = constructEventBody(args);
        handler.handleRequest(eventBody);
    }

    private static String constructEventBody(String[] args) {
        final Options options = new Options();
        options.addRequiredOption("e", "eventBody", true, "Streaming event body in json format");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine line = parser.parse(options, args);
            final String body = line.getOptionValue('e');
            logger.info("{} event body is {}", DOCKER_KEY_PREFIX, body);

            return body;
        } catch (final Exception e) {
            String errorMsg = String.format("Unable to process streaming event. Message: %s", e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg);
        }
    }
}

