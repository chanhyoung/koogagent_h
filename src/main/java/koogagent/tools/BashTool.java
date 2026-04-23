package koogagent.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class BashTool implements ToolSet {

    private static final int TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_LINES = 10_000;

    private static final String[] DANGEROUS_PATTERNS = {
        "rm\\s+-[^\\s]*r",
        "rm\\s+--recursive",
        "\\bsudo\\b",
        "chmod\\s+[0-7]*7[0-7][0-7]",
        ">\\s*/dev/",
        "mkfs",
        "dd\\s+",
        ":\\(\\)\\{",
        "shutdown",
        "reboot",
        "halt",
        "poweroff",
    };

    @Tool(customName = "bash")
    @LLMDescription("bash 명령어를 실행하고 결과를 반환하는 도구입니다.")
    public String bash(@LLMDescription("실행할 bash 명령어 (예: 'ls -la', 'git status')") String command) {
        if (command == null || command.isBlank()) {
            return "오류: 유효하지 않은 명령어입니다.";
        }

        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.matches("(?s).*" + pattern + ".*")) {
                return "오류: 위험한 명령어는 실행할 수 없습니다. (차단된 패턴: " + pattern + ")";
            }
        }

        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);

            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            boolean truncated = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES) {
                        output.append(line).append("\n");
                        lineCount++;
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
                return "오류: 명령어가 " + TIMEOUT_SECONDS + "초 내에 완료되지 않아 강제 종료되었습니다.\n출력:\n" + output;
            }

            int exitCode = process.exitValue();
            String suffix = truncated ? "\n[출력이 " + MAX_OUTPUT_LINES + "줄을 초과하여 잘렸습니다.]" : "";
            return "exit code: " + exitCode + "\n" + output + suffix;

        } catch (IOException e) {
            return "오류: 명령어 실행 중 IO 오류가 발생했습니다: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "오류: 명령어 실행이 인터럽트되었습니다: " + e.getMessage();
        } catch (Exception e) {
            return "오류: 알 수 없는 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
