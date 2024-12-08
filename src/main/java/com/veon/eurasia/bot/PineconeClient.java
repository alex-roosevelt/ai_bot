package com.veon.eurasia.bot;

import okhttp3.*;
import org.json.JSONObject;
import java.util.List;

public class PineconeClient {
  private static final String PINECONE_API_URL = "https://latoken-ai-dfcon8x.svc.aped-4627-b74a.pinecone.io";
  private static final String API_KEY = "PINECONE_API_KEY";

  public static void upsertVector(String id, float[] vector, String metadata) throws Exception {
    OkHttpClient client = new OkHttpClient();

    JSONObject json = new JSONObject();
    json.put("id", id);
    json.put("values", vector);
    if (metadata != null) {
      json.put("metadata", new JSONObject(metadata));
    }

    JSONObject requestBody = new JSONObject();
    requestBody.put("vectors", List.of(json));

    RequestBody body = RequestBody.create(
        MediaType.parse("application/json"), requestBody.toString()
    );

    Request request = new Request.Builder()
        .url(PINECONE_API_URL + "/vectors/upsert")
        .post(body)
        .addHeader("Api-Key", API_KEY)
        .build();

    Response response = client.newCall(request).execute();
    if (!response.isSuccessful()) {
      throw new RuntimeException("Failed to upsert vector: " + response.body().string());
    }
  }

  public static String searchVector(float[] vector, int topK) throws Exception {
    OkHttpClient client = new OkHttpClient();

    JSONObject json = new JSONObject();
    json.put("vector", vector);
    json.put("topK", topK);
    json.put("include_metadata", true);

    RequestBody body = RequestBody.create(
        MediaType.parse("application/json"), json.toString()
    );

    Request request = new Request.Builder()
        .url(PINECONE_API_URL + "/query")
        .post(body)
        .addHeader("Api-Key", API_KEY)
        .build();

    Response response = client.newCall(request).execute();
    if (!response.isSuccessful()) {
      throw new RuntimeException("Failed to search vector: " + response.body().string());
    }

    return response.body().string();
  }

}
