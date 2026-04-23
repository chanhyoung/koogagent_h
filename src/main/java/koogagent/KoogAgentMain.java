package koogagent;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;     

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message.Response;
import ai.koog.rag.base.files.JVMFileSystemProvider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import io.ktor.client.HttpClient;
import io.ktor.client.HttpClientConfig;
import io.ktor.client.engine.apache5.Apache5Engine;
import io.ktor.client.engine.apache5.Apache5EngineConfig;
import kotlin.Unit;
import koogagent.tools.ListFileTool;
import koogagent.tools.ReadFileTool;
import koogagent.tools.EditFileTool;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;

public class KoogAgentMain {
  public static void main(String[] args) throws Exception {
    LLModel model = AnthropicModels.Opus_4_6;

    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    String apiKey = dotenv.get("ANTHROPIC_API_KEY");

    SSLContext sslContext = buildSslContext(
        dotenv.get("SSL_TRUSTSTORE_PATH", "D:/sandbox/koogagent_h/truststore.jks"),
        dotenv.get("SSL_TRUSTSTORE_PASSWORD", "changeit")
    );
    HttpClient ktorClient = buildKtorClient(sslContext);
    AnthropicLLMClient client = new AnthropicLLMClient(apiKey, new AnthropicClientSettings(), ktorClient);
    try (MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(client)) {

      System.out.println("User: ");
      
      // 사용자에게서 직접입력을 받아서 프롬프트로 사용
      String userPrompt = System.console().readLine();

      // 프롬프트생성
      String systemPrompt = "당신은 코딩 에이전트입니다.";

      ToolRegistry toolRegistry = ToolRegistry.builder()
        .tools(new ReadFileTool()) 
        .tools(new ListFileTool()) 
        .tools(new EditFileTool())
        .build();

      AIAgent<String, String> agent = AIAgent.<String, String>builder()
              .promptExecutor(executor)
              .llmModel(model)
              .systemPrompt(systemPrompt)
              .temperature(0.3)
              .toolRegistry(toolRegistry)
              .maxIterations(50)
              .build();
      var response = agent.run(userPrompt);

      System.out.println("Assistant: " + response);
    }
  }

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
