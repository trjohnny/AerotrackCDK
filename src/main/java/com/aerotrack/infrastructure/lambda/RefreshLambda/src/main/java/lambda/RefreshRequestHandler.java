package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.stream.Collectors;

import models.Flight;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;


public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {

    public static final int DAY_PICK_WEIGHT_FACTOR = 30;
    public static final String RYANAIR_API = "https://www.ryanair.com/api/booking/v4/it-it/availability?ADT=1&Origin=%s" +
            "&Destination=%s&IncludeConnectingFlights=false&Disc=0&DateOut=%s&RoundTrip=false&ToUs=AGREED";

    public Void handleRequest(ScheduledEvent event, Context context) {
        // Retrieve JSON from S3

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
        S3Object s3Object = s3Client.getObject(System.getenv("directionBucket"), "airports.json");

        try(InputStream objectData = s3Object.getObjectContent()) {
            String jsonContent = IOUtils.toString(objectData, StandardCharsets.UTF_8);
            JSONObject airportsJson = new JSONObject(jsonContent);

            // Retrieve the "airports" array from the JSON
            JSONArray airportsArray = airportsJson.getJSONArray("airports");

            Random random = new Random();
            // Get a random airport combination
            JSONObject randomAirport = airportsArray.getJSONObject(random.nextInt(airportsArray.length()));
            String fromAirport = randomAirport.getString("from");
            String toAirport = randomAirport.getString("to");

            // Picking a day: it weights sooner days more
            int d = pickNumberWithWeightedProbability(0, 365);

            // Calculate the date 'd' days from today
            LocalDate dateOut = LocalDate.now().plusDays(d);
            String formattedDate = dateOut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Make HTTP request to the Ryanair API
            String ryanairURL = String.format(RYANAIR_API, fromAirport, toAirport, formattedDate);

            URL url = URI.create(ryanairURL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", "rid.sig=jyna6R42wntYgoTpqvxHMK7H+KyM6xLed+9I3KsvYZaVt7P36AL6zp9dGFPu5uVxaIiFpNXrszr+LfNCdY3IT3oCSYLeNv/ujtjsDqOzkY66AL3V6kH2vsK+au12X21HkZ4S8GaG8CoBmm/m0rLsOKYkxtw+U3+ejBaPc15jJjKXnc3owMBg82SNbqyKjVd6Z6qcsoE25p3RmlcaHuHC3GBf1yIGtlqeQun3Mj0vmSURVPQBjK65pge2zGBymoORV6PsmZ0Nabv73gS2VkhG+Eccz5iSIRPrZm5cloi/941TFo8oiXdyqvTMx/ozox2fkvaKB2vd/goSx543TxPdKoGKRLaDY3FoIepe6I46UFvXEZYszzugXHYRnp0lbIn/HPyvHH/iW/TXRrqsELQabKd1hH+Ut1ZgpfsKEtwDVyL7mVvi1qEOqHddSVKCN/439KxQqi9K03dQDm+knQaLRpzZL8EYqCeSaosMOeEhc2CAYWLW2D5jH5iTot0YiyaN3QJFE59H3MYDpGjoZfTfen83yVSpT8DBahOOr6Eibv62bKQXBxel5kIm75dZB26iUzmkBs1Iags291UJ8wpu/GtBD1rghlZoRJt9u3ASkAj3P85dBcV8MwGykVWJ4mCO; mkt=/gb/en/; .AspNetCore.Session=CfDJ8NJy2CjeBXdEiTlIkEo9jP1x6eP6igWNkoeL9uCto1qdz97HQLRlCAJWIhw97YY5uemEBnTrcLNnoB7lqKOnEJRzF%2F4yhGKN9A5COUteaQmZduO6o2whfYqdyD1Qd%2B%2BH6EXjFP4cd0DtvPDwguugs%2Bc684C2CSfw16lyvYFtSkvU; fr-correlation-id=b8d2a2fe-383b-44fa-8c4a-ba6cab8c994d; rid=034ed121-51ec-4cac-9196-97a50ee42d2c; RY_COOKIE_CONSENT=true; STORAGE_PREFERENCES={\"STRICTLY_NECESSARY\":true,\"PERFORMANCE\":false,\"FUNCTIONAL\":false,\"TARGETING\":false,\"SOCIAL_MEDIA\":false,\"PIXEL\":false,\"GANALYTICS\":true,\"__VERSION\":2}; myRyanairID=");
            connection.connect();


            // Read the response
            InputStream responseStream = connection.getInputStream();
            String jsonResponse = IOUtils.toString(responseStream, StandardCharsets.UTF_8);

            // Parse the response JSON and extract flight details
            JSONObject flightDetails = new JSONObject(jsonResponse);
            JSONArray trips = flightDetails.optJSONArray("trips");

            if (trips != null && !trips.isEmpty()) {
                JSONObject trip = trips.getJSONObject(0);

                JSONArray dates = trip.optJSONArray("dates");
                if (dates != null && !dates.isEmpty()) {
                    JSONObject date = dates.getJSONObject(0);

                    JSONArray flights = date.optJSONArray("flights");
                    if (flights != null && !flights.isEmpty()) {
                        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.create();
                        DynamoDbTable<Flight> customerMappedTable = enhancedClient.table(System.getenv("flightTable"), TableSchema.fromBean(Flight.class));

                        WriteBatch.Builder<Flight> builder = WriteBatch.builder(Flight.class).mappedTableResource(customerMappedTable);
                        for (int i = 0; i < flights.length(); i++) {
                            JSONObject flight = flights.getJSONObject(i);

                            String timeFrom = flight.getJSONArray("timeUTC").getString(0);
                            String timeTo = flight.getJSONArray("timeUTC").getString(1);
                            double price = flight.getJSONObject("regularFare").getJSONArray("fares").getJSONObject(0).getDouble("amount");
                            String flightNumber = flight.getString("flightNumber");

                            Flight flightItem = new Flight(fromAirport + "-" + toAirport, timeFrom, timeTo, flightNumber, price);
                            builder.addPutItem(flightItem);

                        }

                        BatchWriteItemEnhancedRequest batchWriteItemEnhancedRequest = BatchWriteItemEnhancedRequest.builder()
                                .writeBatches(builder.build())
                                .build();

                        enhancedClient.batchWriteItem(batchWriteItemEnhancedRequest);
                    }
                }
            }

            // Close resources
            responseStream.close();

        } catch (IOException e) {
            System.err.println(e);
        }

        return null;
    }

    public int pickNumberWithWeightedProbability(int min, int max) {
        Random random = new Random();
        double totalWeight = 0.0;

        // Calculate total weight for normalization
        for (int i = min; i <= max; i++) {
            totalWeight += calculateDayPickWeight(i);
        }

        double randWeight = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;

        // Pick a number based on its weight
        for (int i = min; i <= max; i++) {
            currentWeight += calculateDayPickWeight(i);
            if (randWeight <= currentWeight) {
                return i;
            }
        }

        // This line should not be reached under normal circumstances
        return min;
    }

    public double calculateDayPickWeight(int delayDays) {
        return 1.0 / (1 + ((1.0 / DAY_PICK_WEIGHT_FACTOR) * (1.0 / 365) * delayDays));
    }
}


