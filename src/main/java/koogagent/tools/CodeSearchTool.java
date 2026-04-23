package koogagent.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class CodeSearchTool implements ToolSet {

    private static final int MAX_RESULTS = 50;
    private static final int TIMEOUT_SECONDS = 30;

    @Tool(customName = "codeSearch")
    @LLMDescription("""
        ripgrep을 사용해 코드를 검색합니다. 정규식을 지원합니다.
        파일 경로·라인 번호·매칭 내용을 반환합니다.
        결과는 최대 50개로 제한됩니다.
        
        예시:
        - pattern: "TODO" -> TODO가 포함된 모든 줄 검색
        - pattern: "public.*void" -> public으로 시작하고 void가 포함된 줄
        - pattern: 'static void main', fileType: 'java' -> Java 파일에서 static void main이 포함된 줄
        """)
    public String codeSearch(
            @LLMDescription("검색 패턴 (정규식 지원, 예: 'public.*void', 'TODO')") String pattern,
            @LLMDescription("검색할 경로 (기본값: 현재 디렉토리 '.')") String path,
            @LLMDescription("파일 타입 필터 (예: 'kt', 'java', 'py'). 비워두면 전체 파일 검색") String fileType,
            @LLMDescription("대소문자 구분 여부. true이면 구분, false이면 무시 (기본값: false)") boolean caseSensitive) {

        if (pattern == null || pattern.isBlank()) {
            return "오류: 검색 패턴이 비어있습니다.";
        }

        String searchPath = (path == null || path.isBlank()) ? "." : path;

        List<String> cmd = new ArrayList<>();
        cmd.add("rg");
        cmd.add("--line-number");   // 라인 번호 출력
        cmd.add("--with-filename"); // 파일명 출력
        cmd.add("--color=never");   // 색상 코드 제거 (파싱 편의)
        cmd.add("--no-heading");    // 파일명과 매칭 결과 사이에 빈 줄 제거

        if (!caseSensitive) {
            cmd.add("--ignore-case");
        }

        if (fileType != null && !fileType.isBlank()) {
            cmd.add("--type=" + fileType.trim());
        }

        cmd.add(pattern);
        cmd.add(searchPath);

        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);

            Process process = builder.start();

            List<String> lines = new ArrayList<>();
            boolean truncated = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lines.size() < MAX_RESULTS) {
                        lines.add(line);
                    } else {
                        truncated = true;
                        process.destroyForcibly();
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "오류: 검색이 " + TIMEOUT_SECONDS + "초 내에 완료되지 않아 강제 종료되었습니다.";
            }

            int exitCode = process.exitValue();
            if (exitCode == 2) {
                return "오류: ripgrep 실행 중 오류가 발생했습니다.\n" + String.join("\n", lines);
            }
            if (lines.isEmpty()) {
                return "검색 결과가 없습니다. (pattern: \"" + pattern + "\", path: " + searchPath + ")";
            }

            List<String> results = lines;

            StringBuilder sb = new StringBuilder();
            sb.append("검색 결과: ")
              .append(truncated ? MAX_RESULTS + "개 (총 " + lines.size() + "개 중 상위 " + MAX_RESULTS + "개)" : lines.size() + "개")
              .append(" — pattern: \"").append(pattern).append("\"")
              .append(", path: ").append(searchPath);
            if (fileType != null && !fileType.isBlank()) {
                sb.append(", type: ").append(fileType);
            }
            sb.append("\n\n");

            for (String line : results) {
                sb.append(line).append("\n");
            }

            return sb.toString().stripTrailing();

        } catch (IOException e) {
            return "오류: ripgrep 실행 실패. ripgrep(rg)이 설치되어 있는지 확인하세요: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "오류: 검색이 인터럽트되었습니다: " + e.getMessage();
        } catch (Exception e) {
            return "오류: 알 수 없는 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
