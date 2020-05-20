package com.amazonaws.transcribepublishing;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApi;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiClientBuilder;
import com.amazonaws.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import com.amazonaws.services.apigatewaymanagementapi.model.PostToConnectionResult;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.streamingeventmodel.StreamingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;

import static com.amazonaws.constants.WebSockerMappingDDBConstants.*;

public class WebSocketTranscriptionPublisher implements TranscriptionPublisher{

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

    private String getConnectionId() {
        if(this.connectionId == null) {
            QuerySpec spec = new QuerySpec()
                    .withMaxResultSize(1)
                    .withKeyConditionExpression(FROM_NUMBER + " = :from and " + TO_NUMBER + " = :to")
                    .withValueMap(new ValueMap().withString(":from", detail.getFromNumber()).withString(":to", detail.getToNumber()));

            if (getDDBClient().getTable(WEBSOCKET_MAPPING_TABLE_NAME).query(spec).iterator().hasNext()) {
                Item item = getDDBClient().getTable(WEBSOCKET_MAPPING_TABLE_NAME).query(spec).iterator().next();
                this.connectionId = (String) item.get(CONNECTION_ID);

                logger.info("{} connection is established with id {}, starting transmission", WEB_SOCKET_PUBLISHER_PREFIX, this.connectionId);
            } else {
                logger.debug("{} cannot find connection id in dynamodb table. ", WEB_SOCKET_PUBLISHER_PREFIX);
            }
        }

        return this.connectionId;
    }

    @Override
    public void publish(TranscriptEvent event) {

        List<Result> results = event.transcript().results();

        if (results.size() > 0) {

            Result result = results.get(0);

            if (!result.isPartial()) {
                try {

                    if(getConnectionId() == null) {
                        logger.debug("{} connection id is null.", WEB_SOCKET_PUBLISHER_PREFIX);
                        return;
                    }

                    PostToConnectionRequest request = new PostToConnectionRequest().withConnectionId(this.connectionId).withData(StandardCharsets.UTF_8.encode(buildTranscription(result)));
                    PostToConnectionResult postResult = apigatewayClient.postToConnection(request);

                    logger.debug("{} connection id is {}, post to connection result is {}", WEB_SOCKET_PUBLISHER_PREFIX, this.connectionId, postResult.toString());

                    // No need to process http response.
                } catch (Exception e) {
                    logger.error("{} publish encountered exception, error message: {}", WEB_SOCKET_PUBLISHER_PREFIX, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void publishDone() {

        if(getConnectionId() == null) {
            logger.debug("{} connection id is null.", WEB_SOCKET_PUBLISHER_PREFIX);
            return;
        }

        try {
            String endedMsg = String.format("=== Transcription Ended for call %s in stream %s ===", this.detail.getCallId(), this.detail.getStreamArn());
            PostToConnectionRequest postRequest = new PostToConnectionRequest().withConnectionId(this.connectionId).withData(StandardCharsets.UTF_8.encode(endedMsg));
            PostToConnectionResult postResult = apigatewayClient.postToConnection(postRequest);

            logger.debug("{} post to connection result is {}", WEB_SOCKET_PUBLISHER_PREFIX, postResult.toString());
        } catch (Exception e) {
            logger.error("{} publish done encountered exception, error message: {}", WEB_SOCKET_PUBLISHER_PREFIX, e.getMessage(), e);
        }
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
