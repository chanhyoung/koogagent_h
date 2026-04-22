package koogagent;

import java.util.List;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.message.Message.Response;
import io.github.cdimascio.dotenv.Dotenv;

public class KoogAgentMain {
  public static void main(String[] args) {
    String apiKey = getDotenvApiKey();
    AnthropicLLMClient client = new AnthropicLLMClient(apiKey);
    try (MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(client)) {
      System.out.println("User: ");
      
      // 사용자에게서 직접입력을 받아서 프롬프트로 사용
        String userPrompt = System.console().readLine();

        // 프롬프트생성
        Prompt prompt = Prompt.builder("hello-koog")
          .system("You area a coding agent")
          .user(userPrompt)
          .build();
        List<Response> responses = executor.execute(prompt, AnthropicModels.Opus_4_6);

        System.out.println("KoogAgent: " + responses.get(0).getContent());
    }
  }

  private static String getDotenvApiKey() {
      Dotenv dotenv = Dotenv.configure()
              .directory("./")
              .ignoreIfMissing()
              .load();
      return dotenv.get("ANTHROPIC_API_KEY");
  }
}
