package koogagent.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import koogagent.utils.ResolveFilePath;

public class ReadFileTool implements ToolSet {
  @Tool(customName = "readFile")
  @LLMDescription("주어진 파일 경로를 받고 파일 내용을 응답하는 도구 입니다.")
  // readFile 메서드 구현
  public String readFile(@LLMDescription("읽을 파일의 경로") String filePath) {
    // 파일 경로 유효성 검사
    if (filePath == null || filePath.isBlank()) {
      return "오류: 유효하지 않은 파일 경로입니다.";
    }
    
    // 파일을 읽어서 내용을 반환하는 로직을 구현
    try {
      File file = ResolveFilePath.resolveFilePath(filePath);

      if (!file.exists()) return "오류: 파일이 존재하지 않습니다: " + filePath;
      if (!file.isFile()) return "오류: 지정된 경로는 파일이 아닙니다: " + filePath;
      if (!file.canRead()) return "오류: 읽기 권한이 없습니다: " + filePath;
      
      Path path = Paths.get(filePath);
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      e.printStackTrace();
      return "오류: 파일을 읽을 수 없습니다: " + e.getMessage();
    } catch (Exception e) {
      e.printStackTrace();
      return "오류: 알 수 없는 오류가 발생했습니다: " + e.getMessage();
    }
  }
}
