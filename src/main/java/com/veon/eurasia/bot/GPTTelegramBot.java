package com.veon.eurasia.bot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import okhttp3.*;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;

public class GPTTelegramBot extends TelegramLongPollingBot {
  private static final String TELEGRAM_BOT_TOKEN = "TOKEN";
  private static final String OPENAI_API_KEY = "OPENAI_API_KEY";

  @Override
  public String getBotUsername() {
    return "AI_BOT";
  }

  @Override
  public String getBotToken() {
    return TELEGRAM_BOT_TOKEN;
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      var userMessage = update.getMessage().getText();
      var chatId = update.getMessage().getChatId();

      String botResponse;
      if (userMessage.equalsIgnoreCase("/start")) {
        botResponse = """
                        Привет! Я AI бот
                        """;
      } else {
        try {
          botResponse = processUserQuery(userMessage);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      sendMessage(chatId, botResponse);
    }
  }

  private OkHttpClient createHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // Таймаут на установление соединения
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // Таймаут на чтение данных
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // Таймаут на запись данных
        .build();
  }

  private String getResponseFromGPT(String prompt) {
    var client = createHttpClient();
    var url = "https://api.openai.com/v1/chat/completions";

    String systemMessage = """
            Ты бот, который отвечает на любые вопросы""";

    // Создание тела запроса в виде JSON
    var jsonObject = new JsonObject();
    jsonObject.addProperty("model", "gpt-4");
    jsonObject.addProperty("temperature", 0.7);

    // Формируем сообщения
    var messagesArray = new com.google.gson.JsonArray();

    var systemJson = new JsonObject();
    systemJson.addProperty("role", "system");
    systemJson.addProperty("content", systemMessage);
    messagesArray.add(systemJson);

    var userJson = new JsonObject();
    userJson.addProperty("role", "user");
    userJson.addProperty("content", prompt);
    messagesArray.add(userJson);

    jsonObject.add("messages", messagesArray);

    // Отправка запроса
    var request = new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(jsonObject.toString(), MediaType.get("application/json")))
        .build();

    try (var response = client.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        var responseBody = response.body().string();
        var jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
        return jsonResponse.getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("message")
            .get("content")
            .getAsString();
      } else {
        System.err.println("Ошибка при запросе: " + response.code() + " " + response.message());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return "Произошла ошибка при запросе к OpenAI.";
  }

  private String processUserQuery(String userQuery) throws Exception {
    // Генерация вектора для запроса
    double[] doubleArray = VectorGenerator.generateVector(userQuery);

    float[] floatArray = new float[doubleArray.length];
    for (int i = 0; i < doubleArray.length; i++) {
      floatArray[i] = (float) doubleArray[i];
    }

    // Поиск в Pinecone
    String pineconeResponse = PineconeClient.searchVector(floatArray, 1); // Поиск одного наиболее похожего вектора
    var searchResult = JsonParser.parseString(pineconeResponse).getAsJsonObject();

    // Проверяем, есть ли совпадение
    if (searchResult.has("matches") && !searchResult.getAsJsonArray("matches").isEmpty()) {
      var topMatch = searchResult.getAsJsonArray("matches").get(0).getAsJsonObject();
      float score = topMatch.get("score").getAsFloat();
      if (score > 0.9) { // Если совпадение больше 90%
        return topMatch.getAsJsonObject("metadata").get("answer").getAsString();
      }
    }

    // Если совпадений нет, обращаемся к OpenAI
    String gptResponse = getResponseFromGPT(userQuery);

    // Сохраняем запрос и ответ в Pinecone
    String metadata = new JSONObject()
        .put("question", userQuery)
        .put("answer", gptResponse)
        .toString();
    PineconeClient.upsertVector( "vector_id_" + UUID.randomUUID(), floatArray, metadata);

    return gptResponse;
  }


  private void sendMessage(Long chatId, String text) {
    try {
      execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), text));
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }
}
