package com.amazonaws.kvstranscribestreaming;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
    public static void main(String[] args) {
        final KVSTranscribeStreamingHandler handler = new KVSTranscribeStreamingHandler();
        Optional<TranscribeStreamingContext> optionalDetail = constructStreamingDetail(args);
        optionalDetail.ifPresent(handler::handleRequest);
    }

    private static Optional<TranscribeStreamingContext> constructStreamingDetail(String[] args) {
        final Options options = new Options();
        options.addOption("a", "streamARN", true, "Stream ARN." );
        options.addOption("f", "startFragmentNumber", true, "start fragement number");
        options.addOption("i", "transactionId", true, "transaction Id. UUID");
        options.addOption("c", "callId", true, "CallId. UUID");
        options.addOption("s", "streamingStatus", true, "Streaming Status. i.e. STARTED, ENDED");
        options.addOption("t", "startTime", true, "Streaming Start Time. Format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        options.addOption("e", "event", true, "Streaming event json file");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine line = parser.parse(options, args);

            TranscribeStreamingContext detail;
            final String stringifiedDetail = line.getOptionValue("e");
            if(stringifiedDetail == null) {
                 detail = TranscribeStreamingContext.builder()
                        .streamArn(line.getOptionValue("a"))
                        .startFragmentNumber(line.getOptionValue("f"))
                        .transactionId(line.getOptionValue("i"))
                        .callId(line.getOptionValue("c"))
                        .streamingStatus(line.getOptionValue("s"))
                        .startTime(line.getOptionValue("t"))
                        .platform(TranscriptionPlatform.ECS)
                        .build();
                logger.warn("{} stringifiedDetail is null. Please update the infrastructure.", DOCKER_KEY_PREFIX);
            } else {
                // If event detail includes properties that are not in TranscribeStreamingContext, ignore them.
                ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                detail = mapper.readValue(stringifiedDetail, TranscribeStreamingContext.class);
                detail.setPlatform(TranscriptionPlatform.ECS);
                logger.info("{} detail is {}", DOCKER_KEY_PREFIX, detail.toString());
            }

            return Optional.ofNullable(detail);
        } catch (final org.apache.commons.cli.ParseException e) {
            logger.error(DOCKER_KEY_PREFIX + "Unable to parse arguments. Reason: " + e.getMessage(), e);
        } catch (final Exception e) {
            logger.error(DOCKER_KEY_PREFIX + "Unable to process streaming event. Reason: " + e.getMessage(), e);
        }

        return Optional.empty();
    }
}


