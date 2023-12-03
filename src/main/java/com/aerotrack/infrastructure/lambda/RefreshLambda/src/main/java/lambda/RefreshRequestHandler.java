package lambda;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;


public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {
    public Void handleRequest(ScheduledEvent event, Context context) {
        // Retrieve JSON from S3

        /*
        AmazonS3 s3Client = new AmazonS3Client(new ProfileCredentialsProvider());
        S3Object s3Object = s3Client.getObject("AerotrackBucket", "airports.json");
        InputStream objectData = s3Object.getObjectContent();

        try {
            String jsonContent = IOUtils.toString(objectData, "UTF-8");
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

            URL url = new URL(ryanairURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", ".AspNetCore.Session=CfDJ8JhTggPP%2FPNDmg%2FKnWd5DahyGrfM3KaE%2BDjwAJRX3w2myGY4pjYGu6mYrgIvmw36V5u%2FP9eWo2iHMeM9IL2lzPf%2BArrdo8z4oTYcMpVMQfjJsJg8%2BaJbJbVk8%2BnIxlnbeejjXvK2GiBbSPjZgwn%2BGr0U2SDSin%2B1q1bAHzISJ2b6; path=/; samesite=lax; httponly");
            connection.connect();

            // Read the response
            InputStream responseStream = connection.getInputStream();
            String jsonResponse = IOUtils.toString(responseStream, "UTF-8");

            // Parse the response JSON and extract flight details
            JSONObject flightDetails = new JSONObject(jsonResponse);

            String direction = fromAirport + "-" + toAirport;
            String timestamp = flightDetails.getString("serverTimeUTC");

            // Extract the price from the flight details
            Double price = flightDetails.getJSONArray("trips")
                    .getJSONObject(0)
                    .getJSONArray("dates")
                    .getJSONObject(0)
                    .getJSONArray("flights")
                    .getJSONObject(0)
                    .getJSONObject("regularFare")
                    .getDouble("amount");

            // Create an item and put it into DynamoDB
            // Code to put data into DynamoDB table goes here using dynamoDB object

            // Close resources
            responseStream.close();
            objectData.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        */
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


