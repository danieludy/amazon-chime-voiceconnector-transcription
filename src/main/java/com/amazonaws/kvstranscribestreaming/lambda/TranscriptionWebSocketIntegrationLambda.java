package com.amazonaws.kvstranscribestreaming.lambda;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static com.amazonaws.kvstranscribestreaming.constants.WebSockerMappingDDBConstants.*;

/**
 * Integration function that processes and responds web socket connection request through API Gateway.
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
public class TranscriptionWebSocketIntegrationLambda implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionWebSocketIntegrationLambda.class);
    private static final String LAMBDA_KEY_PREFIX = "TranscriptionWebSocketIntegrationLambda:";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String WEB_SOCKET_MAPPING_TABLE = System.getenv("WEB_SOCKET_MAPPING_TABLE");
    private static final String TRANSCRIBE_ROUTE_KEY = System.getenv("TRANSCRIBE_ROUTE_KEY");
    private static final Regions AWS_REGION = Regions.fromName(System.getenv("AWS_REGION"));
    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent requestEvent, Context context) {
        try {
            logger.info(LAMBDA_KEY_PREFIX + " received request : " + objectMapper.writeValueAsString(requestEvent));
        } catch (JsonProcessingException e) {
            logger.error(LAMBDA_KEY_PREFIX + " Error happened where serializing the event", e);
        }
        logger.info(LAMBDA_KEY_PREFIX + " received context: " + context.toString());

        APIGatewayV2WebSocketResponse responseEvent = new APIGatewayV2WebSocketResponse();
        try {
            String routeKey = requestEvent.getRequestContext().getRouteKey();

            // Put from number, to number and connection Id which backend uses to generate a valid endpoint url
            // and POST transcription back to client.
            if (routeKey.equals(TRANSCRIBE_ROUTE_KEY)) {
                if(requestEvent.getBody() == null) {
                    generateResponse(responseEvent, 400, "Must specify body");
                    return responseEvent;
                }

                Map<String, String> eventBodyMap = objectMapper.readValue(requestEvent.getBody(), Map.class);
                if(eventBodyMap.get("from") == null || eventBodyMap.get("to") == null) {
                    generateResponse(responseEvent, 400, "Must specify from and to numbers");
                    return responseEvent;
                }

                String fromNumber = eventBodyMap.get("from"), toNumber = eventBodyMap.get("to"), connectionId = requestEvent.getRequestContext().getConnectionId();
                DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.standard().withRegion(AWS_REGION.getName()).build());
                Item item = new Item()
                        .withPrimaryKey(FROM_NUMBER, fromNumber, TO_NUMBER, toNumber)
                        .withString(CONNECTION_ID, connectionId)
                        .withString(UPDATE_TIME, Instant.now().toString());

                dynamoDB.getTable(WEB_SOCKET_MAPPING_TABLE).putItem(item);
            }

            generateResponse(responseEvent, 200, "Web socket connection with route key " + routeKey + " has been established");
        } catch (Exception e) {
            logger.error("{} transcription integration failed. Reason: {}", LAMBDA_KEY_PREFIX, e.getMessage(), e);
            generateResponse(responseEvent, 500, "Must specify from and to numbers");
        }

        logger.info("{} response event {}", LAMBDA_KEY_PREFIX, responseEvent);
        return responseEvent;
    }

    private void generateResponse(APIGatewayV2WebSocketResponse responseEvent, Integer statusCode, String message) {
        responseEvent.setHeaders(Collections.singletonMap("timeStamp", String.valueOf(System.currentTimeMillis())));
        responseEvent.setStatusCode(statusCode);
        responseEvent.setBody(message);
    }
}
