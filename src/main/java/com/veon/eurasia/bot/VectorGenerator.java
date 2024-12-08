package com.veon.eurasia.bot;

import okhttp3.*;
import org.json.JSONObject;

public class VectorGenerator {
  private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
  private static final String OPENAI_API_KEY = "OPENAI_API_KEY";

  public static double[] generateVector(String text) throws Exception {
    OkHttpClient client = new OkHttpClient();

    JSONObject json = new JSONObject();
    json.put("model", "text-embedding-ada-002");
    json.put("input", text);

    RequestBody body = RequestBody.create(
        MediaType.parse("application/json"), json.toString()
    );

    Request request = new Request.Builder()
        .url(OPENAI_API_URL)
        .post(body)
        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
        .build();

    Response response = client.newCall(request).execute();
    if (!response.isSuccessful()) {
      throw new RuntimeException("Failed to get vector: " + response.body().string());
    }

    JSONObject responseJson = new JSONObject(response.body().string());
    return responseJson.getJSONArray("data")
        .getJSONObject(0)
        .getJSONArray("embedding")
        .toList()
        .stream()
        .map(o -> ((Number) o).floatValue()) // Преобразуем BigDecimal или другие числа в float
        .mapToDouble(Float::doubleValue)    // Преобразуем в поток double для дальнейшей обработки
        .toArray();
  }
}
