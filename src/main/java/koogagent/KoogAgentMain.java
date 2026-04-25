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

    /**
     * CodingAgent 인스턴스를 생성하기 위한 팩토리 인터페이스.
     * runLoop 내에서 에이전트 생성 및 /clear 시 재생성에 사용된다.
     */
    @FunctionalInterface
    interface AgentFactory {
        CodingAgent create() throws Exception;
    }

    /**
     * 프로그램 진입점(Entry Point).
     * <p>
     * .env 파일에서 환경 변수를 로드하여 다음 설정을 수행한다:
     * <ul>
     *   <li>ANTHROPIC_API_KEY: Anthropic API 인증 키 (필수)</li>
     *   <li>IS_SSL_TRUSTSTORE: 커스텀 SSL TrustStore 사용 여부 (기본값: false)</li>
     *   <li>BASH_PATH: bash 실행 경로 (기본값: "bash")</li>
     * </ul>
     * 설정 완료 후 사용자 입력 루프({@link #runLoop})를 시작한다.
     *
     * @param args 커맨드라인 인자 (사용하지 않음)
     * @throws Exception API 키 미설정 또는 클라이언트 생성 실패 시 예외 발생
     */
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

    /**
     * 사용자 입력을 반복적으로 읽어 CodingAgent와 대화하는 메인 루프.
     * <p>
     * 지원하는 명령어:
     * <ul>
     *   <li><b>exit</b>: 프로그램을 종료한다.</li>
     *   <li><b>/clear</b>: 현재 에이전트를 닫고 새 대화를 시작한다.</li>
     *   <li><b>/memory add &lt;내용&gt;</b>: 에이전트 메모리에 정보를 저장한다.</li>
     *   <li>그 외 입력: 에이전트에게 전달하여 응답을 받는다.</li>
     * </ul>
     * 루프 종료 시 에이전트 리소스를 안전하게 해제(close)한다.
     *
     * @param in      사용자 입력 스트림 (예: System.in)
     * @param out     출력 스트림 (예: System.out)
     * @param factory CodingAgent를 생성하는 팩토리
     * @throws Exception 에이전트 생성 또는 대화 중 예외 발생 시
     */
    static void runLoop(InputStream in, PrintStream out, AgentFactory factory) throws Exception {
        CodingAgent agent = factory.create();
        try {
            out.println("Coding Agent가 시작되었습니다.");
            out.println("명령어: /clear (새 대화 시작), /memory add <내용> (정보 저장), exit (종료)");
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

                if (input.startsWith("/memory add ")) {
                    String content = input.substring("/memory add ".length()).trim();
                    agent.agentMemoryStorage.addMemory(content);
                    out.println("메모리에 저장했습니다: " + content);
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

    /**
     * Anthropic LLM 클라이언트를 생성한다.
     * <p>
     * SSL TrustStore 사용 여부에 따라 두 가지 방식으로 클라이언트를 구성한다:
     * <ul>
     *   <li>TrustStore 사용 시: 커스텀 SSLContext와 Ktor HttpClient를 구성하여 생성</li>
     *   <li>TrustStore 미사용 시: 기본 설정으로 간단히 생성</li>
     * </ul>
     *
     * @param apiKey          Anthropic API 인증 키
     * @param isSslTrustStore 커스텀 SSL TrustStore 사용 여부
     * @param dotenv          .env 환경 변수 (TrustStore 경로 및 비밀번호 참조용)
     * @return 구성된 {@link AnthropicLLMClient} 인스턴스
     * @throws Exception SSL 컨텍스트 생성 실패 시 예외 발생
     */
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

    /**
     * 커스텀 SSLContext를 적용한 Ktor HttpClient를 생성한다.
     * <p>
     * Apache5 엔진을 사용하며, 비동기 TLS 전략과 커넥션 매니저를 구성하여
     * 커스텀 TrustStore 기반의 SSL 통신을 지원한다.
     *
     * @param sslContext 적용할 SSL 컨텍스트
     * @return 구성된 Ktor {@link HttpClient} 인스턴스
     */
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

    /**
     * 지정된 TrustStore 파일로부터 SSLContext를 생성한다.
     * <p>
     * JKS 형식의 TrustStore 파일을 로드하고, TrustManagerFactory를 초기화한 뒤
     * TLS 프로토콜 기반의 SSLContext를 구성하여 반환한다.
     *
     * @param trustStorePath     TrustStore 파일 경로 (JKS 형식)
     * @param trustStorePassword TrustStore 비밀번호
     * @return 초기화된 {@link SSLContext} 인스턴스
     * @throws Exception 파일 로드 실패, 키스토어 초기화 실패 등 예외 발생 시
     */
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
