package koogagent.utils;

import java.io.File;
import java.io.IOException;

public class ResolveFilePath {
  /***
   * 파일 경로를 해석하여 실제 파일 객체를 반환하는 메서드
   * - 절대 경로, 상대 경로, 루트 경로 모두 지원
   * @param path
   * @return
   * @throws IOException
   */
  public static File resolveFilePath(String path) throws IOException {
    File workingDir = new File(System.getProperty("user.dir"));
    File directFile = new File(path);
    if (directFile.exists()) return directFile.getCanonicalFile();

    String trimmedPath = path.replaceAll("^/+", "");
    if (!trimmedPath.equals(path)) {
        File relativeFile = new File(workingDir, trimmedPath);
        if (relativeFile.exists()) return relativeFile.getCanonicalFile();
    }

    return new File(workingDir, path).getCanonicalFile();
  }
}
