package study;

import io.github.cdimascio.dotenv.Dotenv;

public class Test {
    public static void main(String[] args) {
        // .env 파일에서 읽기
        String apiKeyFromDotenv = getDotenvApiKey();
        System.out.println("[.env] API Key: " + mask(apiKeyFromDotenv));

        // 시스템 환경변수에서 읽기
        // String apiKeyFromSystem = getSystemEnvApiKey();
        // System.out.println("[시스템 환경변수] API Key: " + mask(apiKeyFromSystem));
    }

/** 프로젝트 루트의 .env 파일에서 ANTHROPIC_API_KEY를 읽는다. */
    private static String getDotenvApiKey() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();
        return dotenv.get("ANTHROPIC_API_KEY");
    }

    /** 윈도우 시스템 환경변수에서 ANTHROPIC_API_KEY를 읽는다. */
    // private static String getSystemEnvApiKey() {
    //     return System.getenv("ANTHROPIC_API_KEY");
    // }

    private static String mask(String key) {
        if (key == null || key.isBlank()) return "(없음)";
        return key.substring(0, Math.min(10, key.length())) + "...";
    }
}
