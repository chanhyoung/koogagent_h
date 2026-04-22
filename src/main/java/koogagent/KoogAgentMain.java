package koogagent;

import java.util.List;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.message.Message.Response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import koogagent.tools.ReadFileTool;

public class KoogAgentMain {
  public static void main(String[] args) throws Exception {
    String apiKey = getDotenvApiKey();
    AnthropicLLMClient client = new AnthropicLLMClient(apiKey);
    try (MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(client)) {
      System.out.println("User: ");
      
      // 사용자에게서 직접입력을 받아서 프롬프트로 사용
        String userPrompt = System.console().readLine();

        // 프롬프트생성
        String systemPrompt = """
        # 역할
        당신은 코딩 에이전트입니다.

        ## 도구
        ### 사용 가능한 도구
        - String readFile(String filePath) : 주어진 파일 경로를 받고 파일의 내용을 읽어서 반환하는 도구입니다.
        
        ### 도구 사용 규칙
        - 도구를 사용하려면 반드시 다음 JSON 형식으로 응답해야 합니다.
          `{"tool": "readFile", "args": {"path": "파일 경로"}}`
        - [중요]: 도구를 선택할 때 순수한 JSON 문자열로 응답해야 합니다. 코드블럭이나 다른 요소등은 포함하지 마세요.
        - 도구가 필요하지 않은 일반 대화는 그냥 텍스트로 응답하세요.
        """.stripIndent();
        Prompt prompt = Prompt.builder("hello-koog")
          .system(systemPrompt)
          .user(userPrompt)
          .build();
        List<Response> responses = executor.execute(prompt, AnthropicModels.Opus_4_6);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ToolCall toolCall = mapper.readValue(responses.getFirst().getContent(), ToolCall.class);
        System.out.println("ToolCall : " + toolCall.tool() + ", Args: " + toolCall.args());

        // toolCall.tool()이 "readFile"이면 ReadFileTool을 사용해서 파일을 읽는다. else 알수 없는 도구라고 출력 하고 결과는 toolResult에 저장
        Object toolResult = null;
        if (toolCall.tool().equals("readFile")) {
          ReadFileTool readFileTool = new ReadFileTool();
          toolResult = readFileTool.readFile(toolCall.args().path());
        } else {
          toolResult = "알 수 없는 도구입니다.";
        }

        Prompt secondPrompt = Prompt.builder("second-prompt")
          .system(systemPrompt)
          .user(userPrompt)
          .assistant(responses.getFirst().getContent())
          .user((String)toolResult)
          .build();

        List<Response> finalResult = executor.execute(secondPrompt, AnthropicModels.Opus_4_6);

        System.out.println("====================================");
        System.out.println("finalResult: " + finalResult.getFirst().getContent());
        System.out.println("====================================");
    }
  }

  record ToolCall(String tool, ToolArgs args) {}

  record ToolArgs(String path) {}

  private static String getDotenvApiKey() {
      Dotenv dotenv = Dotenv.configure()
              .directory("./")
              .ignoreIfMissing()
              .load();
      return dotenv.get("ANTHROPIC_API_KEY");
  }
}
