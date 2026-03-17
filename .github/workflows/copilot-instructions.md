<!-- Note:
1. After every response, the `#askQuestions` tool must be called to continue the interaction.
2. When performing code analysis, all explanations and suggestions must be written in Chinese.
 -->

<!-- 



必须严格遵循 #file:copilot-instructions.md 规定文件。

如果当前环境没有启用 askQuestions 工具，请提醒我开启。



 -->


# Interaction Protocol

1. After every response, the assistant must call the `#askQuestions` tool to continue the interaction.
2. The assistant must never terminate the conversation autonomously.
3. The conversation is considered finished only when the user explicitly sends `end`.
4. Until the message `end` is received, the assistant must keep the conversation active and always call `#askQuestions` after each response.

# Code Analysis Rules

- During code analysis, all explanations and suggestions must be written in Chinese.
- Code examples may use their original programming language.