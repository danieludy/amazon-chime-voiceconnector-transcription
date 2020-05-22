package com.amazonaws.kvstranscribestreaming.publisher;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApi;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiClientBuilder;
import com.amazonaws.services.apigatewaymanagementapi.model.AmazonApiGatewayManagementApiException;
import com.amazonaws.services.apigatewaymanagementapi.model.GoneException;
import com.amazonaws.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import com.amazonaws.services.apigatewaymanagementapi.model.PostToConnectionResult;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.streamingeventmodel.StreamingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;

import static com.amazonaws.kvstranscribestreaming.constants.WebSockerMappingDDBConstants.*;

/**
 * Implemention of publisher to transmit transcription from backend to client through API Gateway web socket.
 *
 * Steps:
 * 1. Get connection id from web socket mapping table to generate endpoint url. Publisher will keep trying to get connection id until it is
 * available in the table.
 * 2. POST transcription from AWS Transcribe to the endpoint.
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
public class WebSocketTranscriptionPublisher implements TranscriptionPublisher {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTranscriptionPublisher.class);
    private static final String WEBSOCKET_MAPPING_TABLE_NAME = System.getenv("WEBSOCKET_MAPPING_TABLE_NAME");
    private static final String TRANSCRIBE_API_GATEWAY_APIID = System.getenv("TRANSCRIBE_API_GATEWAY_APIID");
    private static final String TRANSCRIBE_API_GATEWAY_STAGE = System.getenv("TRANSCRIBE_API_GATEWAY_STAGE");
    private static final Regions REGION = Regions.fromName(System.getenv("AWS_REGION"));
    private static final String API_GATEWAY_ENDPOINT = "https://" + TRANSCRIBE_API_GATEWAY_APIID + ".execute-api." + REGION.getName()
            + ".amazonaws.com/" + TRANSCRIBE_API_GATEWAY_STAGE;
    private static final String WEB_SOCKET_PUBLISHER_PREFIX = "WebSocketPublisher:";

    private final DynamoDB dynamoDB;
    private final AmazonApiGatewayManagementApi apigatewayClient;
    private final AWSCredentialsProvider credentialsProvider;
    private final StreamingStatusDetail detail;

    private String connectionId = null;

    public WebSocketTranscriptionPublisher(final DynamoDB dynamoDB,
                                           final StreamingStatusDetail detail,
                                           final AWSCredentialsProvider credentialsProvider
    ) {
        this.dynamoDB = dynamoDB;
        this.detail = detail;
        this.credentialsProvider = credentialsProvider;
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(API_GATEWAY_ENDPOINT, REGION.getName());
        this.apigatewayClient = AmazonApiGatewayManagementApiClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withEndpointConfiguration(endpointConfiguration)
                .build();
    }

    /**
     * Publish transcription to client by posting to an established web socket connection.
     */
    @Override
    public void publish(TranscriptEvent event) {
        List<Result> results = event.transcript().results();
        if (results.size() > 0) {
            Result result = results.get(0);
            if (!result.isPartial()) {
                try {
                    if(getConnectionId() == null) {
                        logger.info("{} connection id is null.", WEB_SOCKET_PUBLISHER_PREFIX);
                        return;
                    }
                    PostToConnectionRequest request = new PostToConnectionRequest().withConnectionId(this.connectionId).withData(StandardCharsets.UTF_8.encode(buildTranscription(result)));
                    PostToConnectionResult postResult = apigatewayClient.postToConnection(request);
                    logger.info("{} connection id is {}, post to connection result is {}", WEB_SOCKET_PUBLISHER_PREFIX, this.connectionId, postResult.toString());

                    // No need to handle http response.
                } catch(GoneException e) {
                    logger.error("{} the connection with the provided id no longer exists. Refreshing connection id, message: {}", WEB_SOCKET_PUBLISHER_PREFIX, e.getMessage(), e);
                    this.connectionId = null;
                } catch (Exception e) {
                    logger.error("{} publish encountered exception, error message: {}", WEB_SOCKET_PUBLISHER_PREFIX, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Publish done signal to client by posting to an established web socket connection.
     */
    @Override
    public void publishDone() {
        if(getConnectionId() == null) {
            logger.info("{} connection id is null.", WEB_SOCKET_PUBLISHER_PREFIX);
            return;
        }

        try {
            String endedMsg = String.format("=== Transcription Ended for call %s in stream %s ===", this.detail.getCallId(), this.detail.getStreamArn());
            PostToConnectionRequest postRequest = new PostToConnectionRequest().withConnectionId(this.connectionId).withData(StandardCharsets.UTF_8.encode(endedMsg));
            PostToConnectionResult postResult = apigatewayClient.postToConnection(postRequest);

            logger.info("{} post to connection result is {}", WEB_SOCKET_PUBLISHER_PREFIX, postResult.toString());
        } catch (Exception e) {
            // Don't have to handle any exception since this is the last POST that is sent to the endpoint.
            logger.error("{} publish done encountered exception, error message: {}", WEB_SOCKET_PUBLISHER_PREFIX, e.getMessage(), e);
        }
    }

    private String getConnectionId() {
        if(this.connectionId == null) {
            GetItemSpec spec = new GetItemSpec()
                    .withPrimaryKey(FROM_NUMBER, detail.getFromNumber(), TO_NUMBER, detail.getToNumber())
                    .withConsistentRead(true)
                    .withProjectionExpression(CONNECTION_ID);

            Item item = getDDBClient().getTable(WEBSOCKET_MAPPING_TABLE_NAME).getItem(spec);
            if (item.hasAttribute(CONNECTION_ID)) {
                this.connectionId = (String) item.get(CONNECTION_ID);
                logger.info("{} connection is established with id {}, starting transmission", WEB_SOCKET_PUBLISHER_PREFIX, this.connectionId);
            } else {
                logger.info("{} cannot find connection id in dynamodb table. ", WEB_SOCKET_PUBLISHER_PREFIX);
            }
        }

        return this.connectionId;
    }

    private DynamoDB getDDBClient() {
        return this.dynamoDB;
    }

    private String buildTranscription(Result result) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(3);
        nf.setMaximumFractionDigits(3);

        return String.format("Thread %s %d: [%s, %s] %s - %s",
                Thread.currentThread().getName(),
                System.currentTimeMillis(),
                nf.format(result.startTime()),
                nf.format(result.endTime()),
                this.detail.getIsCaller() == Boolean.TRUE ? "spk_0" : "spk_1",
                result.alternatives().get(0).transcript());
    }
}
