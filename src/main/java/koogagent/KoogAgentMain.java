package koogagent;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import io.github.cdimascio.dotenv.Dotenv;
import io.ktor.client.HttpClient;
import io.ktor.client.HttpClientConfig;
import io.ktor.client.engine.apache5.Apache5Engine;
import io.ktor.client.engine.apache5.Apache5EngineConfig;
import kotlin.Unit;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;

public class KoogAgentMain {

    @FunctionalInterface
    interface AgentFactory {
        CodingAgent create() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.");
        }
        boolean isSslTrustStore = dotenv.get("IS_SSL_TRUSTSTORE", "false").equalsIgnoreCase("true");
        String bashPath = dotenv.get("BASH_PATH", "bash");

        runLoop(System.in, System.out, () -> new CodingAgent(buildClient(apiKey, isSslTrustStore, dotenv), bashPath));
    }

    static void runLoop(InputStream in, PrintStream out, AgentFactory factory) throws Exception {
        CodingAgent agent = factory.create();
        try {
            out.println("Coding Agent가 시작되었습니다.");
            out.println("명령어: /clear (새 대화 시작), exit (종료)");
            out.println();

            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8);
            while (true) {
                out.print("User: ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("exit")) {
                    out.println("종료합니다.");
                    break;
                }

                if (input.equals("/clear")) {
                    agent.close();
                    agent = factory.create();
                    out.println("새로운 대화가 시작되었습니다.");
                    out.println();
                    continue;
                }

                try {
                    String response = agent.chat(input);
                    out.println("Agent: " + response);
                } catch (Exception e) {
                    out.println("오류: " + e);
                }
                out.println();
            }
        } finally {
            agent.close();
        }
    }

    private static AnthropicLLMClient buildClient(String apiKey, boolean isSslTrustStore, Dotenv dotenv) throws Exception {
        if (isSslTrustStore) {
            SSLContext sslContext = buildSslContext(
                dotenv.get("SSL_TRUSTSTORE_PATH", "D:/sandbox/koogagent_h/truststore.jks"),
                dotenv.get("SSL_TRUSTSTORE_PASSWORD", "changeit")
            );
            HttpClient ktorClient = buildKtorClient(sslContext);
            return new AnthropicLLMClient(apiKey, new AnthropicClientSettings(), ktorClient);
        }
        return new AnthropicLLMClient(apiKey);
    }

    private static HttpClient buildKtorClient(SSLContext sslContext) {
        Apache5EngineConfig engineConfig = new Apache5EngineConfig();

        var tlsStrategy = ClientTlsStrategyBuilder.create()
            .setSslContext(sslContext)
            .buildAsync();

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
