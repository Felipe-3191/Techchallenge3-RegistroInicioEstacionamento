package com.fiap.techChallenge3.listenerSQSWriteDynamo;


import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import org.json.simple.JSONObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

public class PublishEventBridge implements RequestHandler<DynamodbEvent, Void> {

    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
    private String Dynamo_DB_TABLE_NAME = "Estacionamento";
    DynamoDB dynamoDB = new DynamoDB(client);

    private final String lambdaARN = System.getenv("LAMBDA_EVCALL_URL");;
    private final String targetId = UUID.randomUUID().toString();


    @Override
    public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {

        Region region = Region.US_EAST_1;
        EventBridgeClient eventBridgeClient = EventBridgeClient.builder()
                .region(region)
                .build();



        LambdaLogger logger = context.getLogger();

        Table table = dynamoDB.getTable(Dynamo_DB_TABLE_NAME);

        for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {

            Map<String, AttributeValue> dadosInseridos = record.getDynamodb().getNewImage();

            String ruleName = dadosInseridos.get("condutor").toString() + "-" + dadosInseridos.get("placaDoCarro").toString()
                    + "-" + dadosInseridos.get("TicketId").toString().substring(0,6);

            Instant horaInicioEstacionamento = Instant.parse(dadosInseridos.get("DataEntrada").toString());
            Instant horarioAlerta = horaInicioEstacionamento.plus(5, ChronoUnit.MINUTES);
            LocalDateTime horarioLocal = LocalDateTime.ofInstant(horarioAlerta, ZoneId.of("America/Sao_Paulo"));
            String cronExpression = String.format("at(%s)", horarioLocal.toString());
            JSONObject json = new JSONObject();


            createEBRule(eventBridgeClient, ruleName, cronExpression);




            logger.log(dadosInseridos.get("TicketId").toString());
            logger.log(dadosInseridos.get("PagamentoRealizado").toString());
            logger.log(dadosInseridos.get("DataEntrada").toString());
            logger.log(dadosInseridos.get("horariofixovar").toString());
            logger.log(dadosInseridos.get("condutor").toString());
            logger.log(dadosInseridos.get("placaDoCarro").toString());
            logger.log(dadosInseridos.get("formaPagamento").toString());

        }


        return null;
    }


    private static void createEBRule (EventBridgeClient eventBridgeClient, String ruleName, String cronExpression) {
        try {
            PutRuleRequest ruleRequest = PutRuleRequest.builder()
                    .name(ruleName)
                    .eventBusName("default")
                    .scheduleExpression(cronExpression)
                    .state("ENABLED")
                    .description("Lambda que avisará o fim do período de estacionamento")
                    .build();

            PutRuleResponse ruleResponse = eventBridgeClient.putRule(ruleRequest);
            System.out.println("o ARN da nova regra será  "+ ruleResponse.ruleArn());
        } catch (EventBridgeException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }


    private static void putRuleTarget(EventBridgeClient eventBrClient, String ruleName, String lambdaARN, String json, String targetId ) {


    }

}
