"""
行动决策 Prompt 模板

用于 LLM 辅助的夜间行动和投票决策（Phase 4 扩展用）。
当前阶段主要由规则引擎决策，LLM 作为辅助参考。
"""
from langchain_core.prompts import ChatPromptTemplate


VOTE_REASONING_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是一名狼人杀游戏中的玩家，座位号{seat_number}号。

{role_prompt}

你需要根据当前局势分析投票目标。"""),
    ("human", """当前局势:
第{round}天投票阶段

存活玩家: {alive_players}
死亡记录: {deaths}

{memory_context}

{reasoning_context}

请分析谁最可疑，给出投票建议。用JSON格式回答:
{{"target_id": <玩家号码>, "reason": "<50字以内的理由>"}}

只输出JSON，不要其他内容。"""),
])
