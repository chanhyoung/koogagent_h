package koogagent.llm;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor;
import io.github.cdimascio.dotenv.Dotenv;
import io.ktor.client.HttpClient;
import io.ktor.client.HttpClientConfig;
import io.ktor.client.engine.apache5.Apache5Engine;
import io.ktor.client.engine.apache5.Apache5EngineConfig;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

@Slf4j
@Service
public class AnthropicMain {
    static AIAgent<String, String> agent;

    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        SSLContext sslContext = buildSslContext(
            dotenv.get("SSL_TRUSTSTORE_PATH", "D:/sandbox/koogagent_h/truststore.jks"),
            dotenv.get("SSL_TRUSTSTORE_PASSWORD", "changeit")
        );

        String systemPrompt = """
        # 역할
        당신은 코딩 에이전트입니다.

        ## 도구
        ### 사용 가능한 도구
        - String readFile(String filePath) : 주어진 파일 경로를 받고 파일의 내용을 읽어서 반환하는 도구입니다.
        
        ### 도구 사용 규칙
        - 도구를 사용하려면 반드시 다음 JSON 형식으로 응답해야 합니다.
          `{"tool": "readFile", "args": {"path": "파일 경로"}}`
        - [중요]: 도구를 선택할 때 순수한 JSON 문자열로 응답해야 합니다. 코드블럭이나 다른 요소등은 포함하지 마세요.
        - 도구가 필요하지 않은 일반 대화는 그냥 텍스트로 응답하세요.
        """.stripIndent();

        agent = AIAgent.builder()
                .promptExecutor(buildExecutor(apiKey, sslContext))
                .llmModel(AnthropicModels.Opus_4_6)
                .systemPrompt(systemPrompt)
                .maxIterations(10)
                .build();

        log.info("Koog Agent initialized with Anthropic Claude Opus_4_6");

        System.out.print("User: ");

        // 사용자에게서 직접입력을 받아서 프롬프트로 사용
        String userPrompt = System.console().readLine();

        String result = agent.run(userPrompt);
        log.info("Agent result: {}", result);

    }

    private static SingleLLMPromptExecutor buildExecutor(String apiKey, SSLContext sslContext) {
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

        HttpClient ktorClient = new HttpClient(new Apache5Engine(engineConfig), new HttpClientConfig<>());
        AnthropicLLMClient client = new AnthropicLLMClient(apiKey, new AnthropicClientSettings(), ktorClient);
        return new SingleLLMPromptExecutor(client);
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
