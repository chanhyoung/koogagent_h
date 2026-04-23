package koogagent;

import java.io.FileInputStream;
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

    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");
        boolean isSslTrustStore = dotenv.get("IS_SSL_TRUSTSTORE", "false").equalsIgnoreCase("true");

        AnthropicLLMClient client = buildClient(apiKey, isSslTrustStore, dotenv);

        try (CodingAgent agent = new CodingAgent(client)) {
            System.out.println("Coding Agent가 시작되었습니다. 종료하려면 'exit'을 입력하세요.");
            System.out.println();

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("User: ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;
                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("종료합니다.");
                    break;
                }

                try {
                    String response = agent.chat(input);
                    System.out.println("Agent: " + response);
                } catch (Exception e) {
                    System.out.println("오류: " + e.getMessage());
                }
                System.out.println();
            }
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
