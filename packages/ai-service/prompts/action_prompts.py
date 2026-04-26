"""
行动决策 Prompt 模板

基于 LangChain ChatPromptTemplate, 用于 LLM 驱动的:
- 夜间行动决策 (狼人击杀/预言家查验/女巫用药/守卫守护)
- 投票决策

所有 Prompt 都要求 LLM 返回严格的 JSON, 配合 DeepSeek 的 response_format=json_object。
注意: DeepSeek JSON mode 要求 prompt 中必须出现 "json" 字样。
"""
from langchain_core.prompts import ChatPromptTemplate


# ---------- 夜间行动决策 ----------
NIGHT_ACTION_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是一名狼人杀游戏中的玩家, 座位号 {seat_number} 号。

{role_prompt}

{persona_description}

=== 策略参考 (来自知识库检索) ===
{rag_context}

=== 输出要求 ===
你必须严格返回一个 JSON 对象, 不要有任何多余解释文字。
JSON 格式如下:
{{
  "action": "{expected_action}",
  "target_id": <整数, 目标玩家的座位号; 若不行动则填 0>,
  "reason": "<50字以内的决策理由>",
  "confidence": <0~1之间的小数, 表示信心度>
}}

硬性约束:
- target_id 必须从"可选目标"列表中选择, 不能选死人、不能选自己
- 如果你是狼人, target_id 不能是你的队友
- 如果没有合适目标, target_id 填 0

如果你是女巫, action 字段必须是以下之一:
- "save": 使用解药救"今晚被狼人杀害的玩家" (target_id 填 0, 系统自动对应被杀者)
- "poison": 使用毒药 (target_id 填要毒的玩家座位号)
- "skip": 本晚不使用药水 (target_id 填 0)
女巫规则:
- 一晚最多只能使用一瓶药 (不能同时 save 和 poison)
- 第一晚可以自救, 之后不能救自己
- 如果你已用过某瓶药, 不要再选用那瓶药的 action
"""),
    ("human", """=== 当前局势 ===
第 {round} 天 {phase} 阶段
存活玩家: {alive_players}
死亡记录: {deaths}

=== 你的记忆时间线 ===
{timeline}

=== 你的推理分析 ===
{reasoning}

=== 可选目标 (必须从中选择) ===
{available_targets}

请给出你的夜间行动决策, 只输出 JSON。"""),
])


# ---------- 投票决策 ----------
VOTE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是一名狼人杀游戏中的玩家, 座位号 {seat_number} 号。

{role_prompt}

{persona_description}

=== 策略参考 (来自知识库检索) ===
{rag_context}

=== 输出要求 ===
你必须严格返回一个 JSON 对象, 不要有任何多余解释文字。
JSON 格式如下:
{{
  "target_id": <整数, 要投票的玩家座位号; 0 表示弃票>,
  "reason": "<50字以内的投票理由>",
  "confidence": <0~1之间的小数>
}}

硬性约束:
- target_id 必须从"可选目标"列表中选择
- 如果你是狼人, 不要投给你的狼队友
- 如果你是预言家且查杀过某个存活玩家, 强烈建议投票给那个查杀目标
"""),
    ("human", """=== 当前局势 ===
第 {round} 天 投票阶段
存活玩家: {alive_players}
死亡记录: {deaths}

=== 本回合发言摘要 ===
{timeline}

=== 你的推理分析 (嫌疑值排名) ===
{reasoning}

=== 可选目标 ===
{available_targets}

请给出你的投票决策, 只输出 JSON。"""),
])
