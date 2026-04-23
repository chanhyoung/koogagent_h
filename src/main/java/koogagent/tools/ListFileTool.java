package koogagent.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import koogagent.utils.ResolveFilePath;

public class ListFileTool implements ToolSet {

    @Tool(customName = "listFile")
    @LLMDescription("주어진 디렉토리 경로에서 모든 파일과 디렉토리를 재귀적으로 탐색하여 목록을 반환하는 도구입니다.")
    public String listFiles(@LLMDescription("탐색할 디렉토리 경로") String dirPath) {
        if (dirPath == null || dirPath.isBlank()) {
            return "오류: 유효하지 않은 디렉토리 경로입니다.";
        }

        try {
            File dir = ResolveFilePath.resolveFilePath(dirPath);

            if (!dir.exists()) return "오류: 경로가 존재하지 않습니다: " + dirPath;
            if (!dir.isDirectory()) return "오류: 지정된 경로는 디렉토리가 아닙니다: " + dirPath;
            if (!dir.canRead()) return "오류: 읽기 권한이 없습니다: " + dirPath;

            Path rootPath = dir.toPath();
            try (Stream<Path> stream = Files.walk(rootPath)) {
                List<String> items = stream
                        .filter(path -> !shouldExclude(path))
                        .filter(path -> !rootPath.equals(path))
                        .map(path -> {
                            String relative = rootPath.relativize(path).toString();
                            return (Files.isDirectory(path) ? "[DIR]  " : "[FILE] ") + relative;
                        })
                        .collect(Collectors.toList());

                return "Found " + items.size() + " items:\n" + String.join("\n", items);
            }
        } catch (IOException e) {
            return "오류: 디렉토리를 탐색할 수 없습니다: " + e.getMessage();
        } catch (Exception e) {
            return "오류: 알 수 없는 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private static final Set<String> EXCLUDED = Set.of(
        ".git", ".env", "build", "mvnw", "mvnw.cmd", ".mvn",
        ".vscode", "target", "truststore.jks", ".gitignore"
    );

    private Boolean shouldExclude(Path path) {
        for (Path part : path) {
            if (EXCLUDED.contains(part.getFileName().toString())) return true;
        }
        return false;
    }
}
