package koogagent;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;     

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message.Response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import io.ktor.client.HttpClient;
import io.ktor.client.HttpClientConfig;
import io.ktor.client.engine.apache5.Apache5Engine;
import io.ktor.client.engine.apache5.Apache5EngineConfig;
import kotlin.Unit;
import koogagent.tools.ReadFileTool;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;

public class KoogAgentMain {
  public static void main(String[] args) throws Exception {
    LLModel model = AnthropicModels.Opus_4_6;
    var maxIteration = 10;
    var iteration = 0;

    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    String apiKey = dotenv.get("ANTHROPIC_API_KEY");
    // AnthropicLLMClient client = new AnthropicLLMClient(apiKey);

    SSLContext sslContext = buildSslContext(
        dotenv.get("SSL_TRUSTSTORE_PATH", "D:/sandbox/koogagent_h/truststore.jks"),
        dotenv.get("SSL_TRUSTSTORE_PASSWORD", "changeit")
    );
    HttpClient ktorClient = buildKtorClient(sslContext);
    AnthropicLLMClient client = new AnthropicLLMClient(apiKey, new AnthropicClientSettings(), ktorClient);
    try (MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(client)) {

      List<Map.Entry<String, String>> conversationHistory = new ArrayList<>();  

      System.out.println("User: ");
      
      // 사용자에게서 직접입력을 받아서 프롬프트로 사용
      String userPrompt = System.console().readLine();

      // 프롬프트생성
      String systemPrompt = """
      # 역할
      당신은 코딩 에이전트입니다.

      ## 도구
      ### 사용 가능한 도구
      - String readFile(String filePath) : 주어진 파일 경로를 받고 파일의 내용을 읽어서 반환하는 도구입니다.
      
      ### 도구 사용 규칙
      - 도구를 사용하려면 반드시 다음 JSON 형식으로 응답해야 합니다. 
        `{"tool": "readFile", "args": {"path": "파일 경로"}}`
      - *중요*: 응답결과는 반드시 JSON 문자열만 응답하고 다른 설명은 추가하지 말것.
      - 도구가 필요하지 않은 일반 대화는 그냥 텍스트로 응답하세요.
      """.stripIndent();
      
      while (maxIteration > iteration) {

        var promptBuilder = Prompt.builder("agent-loop")
            .system(systemPrompt)
            .user(userPrompt);
            // 이전 대화 이력 추가
            for (var entry : conversationHistory) {
                promptBuilder = promptBuilder.assistant(entry.getKey())
                                              .user(entry.getValue());
            }
        Prompt currentPrompt = promptBuilder.build();

        List<Response> finalResult = executor.execute(
          currentPrompt, 
          model
        );

        var llmResponse = finalResult.getFirst().getContent();
        System.out.println("121라인 : llmResponse: " + llmResponse);

        ToolCall toolCall = parseToolCall(llmResponse);

        if (toolCall != null) {
          // toolCall.tool()이 "readFile"이면 ReadFileTool을 사용해서 파일을 읽는다. else 알수 없는 도구라고 출력 하고 결과는 toolResult에 저장
          Object toolResult = null;
          if (toolCall.tool().equals("readFile")) {
            ReadFileTool readFileTool = new ReadFileTool();
            toolResult = readFileTool.readFile(toolCall.args().path());
          } else {
            toolResult = "알 수 없는 도구입니다.";
          }
          conversationHistory.add(Map.entry(llmResponse, (String) toolResult));
          iteration++;
        } else {
          System.out.println("Assistant: " + llmResponse);
          break;
        }
      }
    }
  }

  private static ToolCall parseToolCall(String response) {
    try {
      int start = response.indexOf("{\"tool\":");
      if (start == -1) return null;

      int depth = 0;
      int end = -1;
      for (int i = start; i < response.length(); i++) {
        if (response.charAt(i) == '{') depth++;
        else if (response.charAt(i) == '}') {
          if (--depth == 0) { end = i + 1; break; }
        }
      }
      if (end == -1) return null;

      String json = response.substring(start, end);
      ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      return mapper.readValue(json, ToolCall.class);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  record ToolCall(String tool, ToolArgs args) {}

  record ToolArgs(String path) {}

  private static HttpClient buildKtorClient(SSLContext sslContext) {
      Apache5EngineConfig engineConfig = new Apache5EngineConfig();

      var tlsStrategy = ClientTlsStrategyBuilder.create()
              .setSslContext(sslContext)
              .build();

      var connManager = PoolingAsyncClientConnectionManagerBuilder.create()
              .setTlsStrategy(tlsStrategy)
              .build();

      engineConfig.customizeClient(builder -> {
          builder.setConnectionManager(connManager);
          return Unit.INSTANCE;
      });

      return new HttpClient(new Apache5Engine(engineConfig), new HttpClientConfig<>());
  }

  private static SSLContext buildSslContext(String trustStorePath, String trustStorePassword) throws Exception {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      try (FileInputStream fis = new FileInputStream(trustStorePath)) {
          keyStore.load(fis, trustStorePassword.toCharArray());
      }
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), null);
      return sslContext;
  }
}
