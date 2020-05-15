package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.streamingeventmodel.StreamingStatus;
import com.amazonaws.streamingeventmodel.StreamingStatusStartedDetail;
import com.amazonaws.transcribestreaming.KVSByteToAudioEventSubscription;
import com.amazonaws.transcribestreaming.StreamTranscriptionBehaviorImpl;
import com.amazonaws.transcribestreaming.TranscribeStreamingRetryClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
public class KVSTranscribeStreamingHandler {

    private static final int CHUNK_SIZE_IN_KB = 4;
    private static final Regions REGION = Regions.fromName(System.getenv("AWS_REGION"));
    private static final Regions TRANSCRIBE_REGION = Regions.fromName(System.getenv("AWS_REGION"));
    private static final String TRANSCRIBE_ENDPOINT = "https://transcribestreaming." + TRANSCRIBE_REGION.getName()
            + ".amazonaws.com";
    private static final String RECORDINGS_BUCKET_NAME = System.getenv("RECORDINGS_BUCKET_NAME");
    private static final String IS_TRANSCRIBE_ENABLED = System.getenv("IS_TRANSCRIBE_ENABLED");
    private static final String RECORDINGS_KEY_PREFIX = "voiceConnectorToKVS_";
    private static final boolean CONSOLE_LOG_TRANSCRIPT_FLAG = true;
    private static final boolean RECORDINGS_PUBLIC_READ_ACL = false;

    // Lambda maximum timeout is 900 seconds(15 minutes). 5-second buffer is for handler's post actions(stream close, audio upload, etc)
    private static final int LAMBDA_RECORDING_TIMEOUT_IN_SECOND = 895;

    private static final Logger logger = LoggerFactory.getLogger(KVSTranscribeStreamingHandler.class);
    public static final MetricsUtil metricsUtil = new MetricsUtil(AmazonCloudWatchClientBuilder.defaultClient());
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // SegmentWriter saves Transcription segments to DynamoDB
    private TranscribedSegmentWriter segmentWriter = null;

    private final Platform platform;
    private final Boolean shouldWriteAudioToFile = Boolean.TRUE;

    private static final DynamoDB dynamoDB = new DynamoDB(
            AmazonDynamoDBClientBuilder.standard().withRegion(REGION.getName()).build());

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public KVSTranscribeStreamingHandler(final Platform platform) {
        this.platform = platform;
    }

    public String handleRequest(String eventBody) {
        try {

            Map<String, Object> eventBodyMap = objectMapper.readValue(eventBody, Map.class);
            Map<String, String> eventDetail = (Map) eventBodyMap.get("detail");

            String streamingStatus = eventDetail.get("streamingStatus");
            String transactionId = eventDetail.get("transactionId");
            logger.info("Received STARTED event");

            if (StreamingStatus.STARTED.name().equals(streamingStatus)) {
                final StreamingStatusStartedDetail streamingStatusStartedDetail = objectMapper.convertValue(eventDetail,
                        StreamingStatusStartedDetail.class);
                logger.info("[{}] Streaming status {} , EventDetail: {}", transactionId, streamingStatus, streamingStatusStartedDetail);
                startKVSToTranscribeStreaming(streamingStatusStartedDetail);
            }

            logger.info("Finished processing request");
        } catch (Exception e) {
            logger.error("KVS to Transcribe Streaming failed with: ", e);
            return "{ \"result\": \"Failed\" }";
        }
        return "{ \"result\": \"Success\" }";
    }

    /**
     * Starts streaming between KVS and Transcribe The transcript segments are
     * continuously saved to the Dynamo DB table At end of the streaming session,
     * the raw audio is saved as an s3 object
     *
     * @param detail
     * @throws Exception
     */
    private void startKVSToTranscribeStreaming(StreamingStatusStartedDetail detail) throws Exception {

        final String transactionId = detail.getTransactionId();
        final String callId = detail.getCallId();
        final String streamArn = detail.getStreamArn();
        final String startFragmentNumber = detail.getStartFragmentNumber();
        final String startTime = detail.getStartTime();


        Path saveAudioFilePath = Paths.get("/tmp",
                transactionId + "_" + callId + "_" + DATE_FORMAT.format(new Date()) + ".raw");
        FileOutputStream fileOutputStream = new FileOutputStream(saveAudioFilePath.toString());

        InputStream kvsInputStream = KVSUtils.getInputStreamFromKVS(streamArn, REGION, startFragmentNumber,
                getAWSCredentials());
        StreamingMkvReader streamingMkvReader = StreamingMkvReader
                .createDefault(new InputStreamParserByteSource(kvsInputStream));

        KVSTransactionIdTagProcessor tagProcessor = new KVSTransactionIdTagProcessor(transactionId);
        FragmentMetadataVisitor fragmentVisitor = FragmentMetadataVisitor.create(Optional.of(tagProcessor));

        if (Boolean.valueOf(IS_TRANSCRIBE_ENABLED)) {
            try (TranscribeStreamingRetryClient client = new TranscribeStreamingRetryClient(getTranscribeCredentials(),
                    TRANSCRIBE_ENDPOINT, TRANSCRIBE_REGION, metricsUtil)) {

                logger.info("Calling Transcribe service..");

                segmentWriter = new TranscribedSegmentWriter(detail, dynamoDB, CONSOLE_LOG_TRANSCRIPT_FLAG);
                CompletableFuture<Void> result = client.startStreamTranscription(
                        // since we're definitely working with telephony audio, we know that's 8 kHz
                        getRequest(8000),
                        new KVSAudioStreamPublisher(streamingMkvReader, transactionId, fileOutputStream, tagProcessor,
                                fragmentVisitor, this.shouldWriteAudioToFile),
                        new StreamTranscriptionBehaviorImpl(segmentWriter));

                // There is no timeout limit for transcription running on ECS. Since Lambda doesn't support function with more than 15 mins
                // Set up a timeout here so that there is enough time for the audio to be uploaded in S3 before function got destoryed.
                if(this.platform.equals(Platform.ECS)) {
                    result.get();
                } else if(this.platform.equals(Platform.LAMBDA)){
                    result.get(LAMBDA_RECORDING_TIMEOUT_IN_SECOND, TimeUnit.SECONDS);
                }
            } catch (TimeoutException e) {
                logger.debug("Timing out KVS to Transcribe Streaming after 900 sec");
            } catch (Exception e) {
                logger.error("Error during streaming: ", e);
                throw e;

            } finally {
                if (this.shouldWriteAudioToFile) {
                    closeFileAndUploadRawAudio(kvsInputStream, fileOutputStream, saveAudioFilePath, transactionId, startTime);
                }
            }
        } else {
            try {
                logger.info("Transcribe is not enabled; saving audio bytes to location");

                while(true)
                {
                    ByteBuffer outputBuffer = KVSUtils.getByteBufferFromStream(streamingMkvReader, fragmentVisitor, tagProcessor,
                            CHUNK_SIZE_IN_KB);

                    if (outputBuffer.remaining() > 0) {
                        //Write audioBytes to a temporary file as they are received from the stream
                        byte[] audioBytes = new byte[outputBuffer.remaining()];
                        outputBuffer.get(audioBytes);
                        fileOutputStream.write(audioBytes);
                    } else {
                        break;
                    }
                }

            } finally {
                closeFileAndUploadRawAudio(kvsInputStream, fileOutputStream, saveAudioFilePath, transactionId, startTime);
            }
        }
    }

    /**
     * Closes the FileOutputStream and uploads the Raw audio file to S3
     *
     * @param kvsInputStream
     * @param fileOutputStream
     * @param saveAudioFilePath
     * @param transactionId
     * @throws IOException
     */
    private void closeFileAndUploadRawAudio(InputStream kvsInputStream, FileOutputStream fileOutputStream,
            Path saveAudioFilePath, String transactionId, String startTime) throws IOException {

        kvsInputStream.close();
        fileOutputStream.close();

        // Upload the Raw Audio file to S3
        if (new File(saveAudioFilePath.toString()).length() > 0) {
            AudioUtils.uploadRawAudio(REGION, RECORDINGS_BUCKET_NAME, RECORDINGS_KEY_PREFIX,
                    saveAudioFilePath.toString(), transactionId, startTime, RECORDINGS_PUBLIC_READ_ACL, getAWSCredentials());
        } else {
            logger.info("Skipping upload to S3. Audio file has 0 bytes: " + saveAudioFilePath);
        }
    }

    /**
     * @return AWS credentials to be used to connect to s3 (for fetching and
     *         uploading audio) and KVS
     */
    private static AWSCredentialsProvider getAWSCredentials() {
        return DefaultAWSCredentialsProviderChain.getInstance();
    }

    /**
     * @return AWS credentials to be used to connect to Transcribe service. This
     *         example uses the default credentials provider, which looks for
     *         environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)
     *         or a credentials file on the system running this program.
     */
    private static AwsCredentialsProvider getTranscribeCredentials() {
        return DefaultCredentialsProvider.create();
    }

    /**
     * Build StartStreamTranscriptionRequestObject containing required parameters to
     * open a streaming transcription request, such as audio sample rate and
     * language spoken in audio
     *
     * @param mediaSampleRateHertz sample rate of the audio to be streamed to the
     *                             service in Hertz
     * @return StartStreamTranscriptionRequest to be used to open a stream to
     *         transcription service
     */
    private static StartStreamTranscriptionRequest getRequest(Integer mediaSampleRateHertz) {
        return StartStreamTranscriptionRequest.builder().languageCode(LanguageCode.EN_US.toString())
                .mediaEncoding(MediaEncoding.PCM).mediaSampleRateHertz(mediaSampleRateHertz).build();
    }

    /**
     * KVSAudioStreamPublisher implements audio stream publisher. It emits audio
     * events from a KVS stream asynchronously in a separate thread
     */
    private static class KVSAudioStreamPublisher implements Publisher<AudioStream> {
        private final StreamingMkvReader streamingMkvReader;
        private String callId;
        private OutputStream outputStream;
        private KVSTransactionIdTagProcessor tagProcessor;
        private FragmentMetadataVisitor fragmentVisitor;
        private boolean shouldWriteToOutputStream;

        private KVSAudioStreamPublisher(StreamingMkvReader streamingMkvReader, String callId, OutputStream outputStream,
                                        KVSTransactionIdTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
                boolean shouldWriteToOutputStream) {
            this.streamingMkvReader = streamingMkvReader;
            this.callId = callId;
            this.outputStream = outputStream;
            this.tagProcessor = tagProcessor;
            this.fragmentVisitor = fragmentVisitor;
            this.shouldWriteToOutputStream = shouldWriteToOutputStream;
        }

        @Override
        public void subscribe(Subscriber<? super AudioStream> s) {
            s.onSubscribe(new KVSByteToAudioEventSubscription(s, streamingMkvReader, callId, outputStream, tagProcessor,
                    fragmentVisitor, shouldWriteToOutputStream));
        }
    }

}
