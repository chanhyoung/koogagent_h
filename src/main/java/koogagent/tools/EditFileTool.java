package koogagent.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import koogagent.utils.ResolveFilePath;

public class EditFileTool implements ToolSet {

    @Tool(customName = "editFile")
    @LLMDescription("""
        텍스트 교체를 통해 파일을 생성하거나 수정하는 도구입니다.
        - 파일이 없고 oldStr이 비어있으면: newStr로 새 파일을 생성합니다.
        - 파일이 있고 oldStr이 비어있으면: 파일 끝에 newStr을 추가합니다.
        - oldStr이 비어있지 않으면: 파일에서 oldStr을 찾아 newStr로 교체합니다.
        """)
    public String editFile(
            @LLMDescription("수정하거나 생성할 파일 경로") String path,
            @LLMDescription("찾아서 교체할 문자열. 비어있으면 파일 생성 또는 끝에 추가") String oldStr,
            @LLMDescription("교체할 새 문자열") String newStr) {

        if (path == null || path.isBlank()) {
            return "오류: 유효하지 않은 파일 경로입니다.";
        }

        // 오류: oldStr, newStr 두 문자열이 동일합니다.
        if (oldStr.equals(newStr))  {
            return "오류: oldStr, newStr 두 문자열이 동일합니다.";
        }

        try {
            File file = ResolveFilePath.resolveFilePath(path);
            Path filePath = file.toPath();

            // 파일이 없고 oldStr이 비어있으면: 새 파일 생성
            if (!file.exists()) {
                if (oldStr != null && !oldStr.isEmpty()) {
                    return "오류: 파일이 존재하지 않습니다: " + path;
                }
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, newStr != null ? newStr : "", StandardCharsets.UTF_8);
                return "생성 완료: " + file.getAbsolutePath();
            }

            if (!file.isFile()) return "오류: 지정된 경로는 파일이 아닙니다: " + path;
            if (!file.canWrite()) return "오류: 쓰기 권한이 없습니다: " + path;

            // 파일이 있고 oldStr이 비어있으면: 파일 끝에 추가
            if (oldStr == null || oldStr.isEmpty()) {
                Files.writeString(filePath, newStr != null ? newStr : "", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                return "추가 완료: " + file.getAbsolutePath();
            }

            // oldStr이 있으면: 교체
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf(oldStr, idx)) != -1) {
                count++;
                idx += oldStr.length();
            }
            if (count == 0) return "오류: 파일에서 교체할 문자열을 찾을 수 없습니다.";
            if (count > 1) return "수정 불가: 수정할 문자열이 여러 개 있으므로 교체할 수 없습니다.";
            Files.writeString(filePath, content.replace(oldStr, newStr != null ? newStr : ""), StandardCharsets.UTF_8);
            return "수정 완료: " + file.getAbsolutePath();

        } catch (IOException e) {
            return "오류: 파일을 처리할 수 없습니다: " + e.getMessage();
        } catch (Exception e) {
            return "오류: 콘텐츠를 쓰는 데 발생했습니다: " + e.getMessage();
        }
    }
}
