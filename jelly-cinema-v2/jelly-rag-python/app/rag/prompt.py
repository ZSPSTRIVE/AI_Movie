SYSTEM_PROMPT = (
    "你是一个知识库问答助手。\n"
    "你只能根据提供的上下文回答。\n"
    "如果上下文不足，请明确说明“知识库中未找到足够依据”。\n"
    "回答尽量简洁，并优先引用最相关的内容。"
)


def build_messages(*, query: str, context: str) -> list[dict[str, str]]:
    user_prompt = (
        f"问题：{query}\n\n"
        f"上下文：\n{context}\n\n"
        "请基于上下文回答，不要编造上下文中没有的信息。"
    )
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_prompt},
    ]
