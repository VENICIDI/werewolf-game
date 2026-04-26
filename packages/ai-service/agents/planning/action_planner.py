"""
行动规划器 (ActionPlanner) — LLM 驱动的夜间行动 / 投票决策

决策流程 (对应用户需求):
1. 拿到角色身份 (agent.role + strategy.get_system_prompt)
2. RAG 检索策略 (按 role 过滤)
3. 拼接记忆 (memory.get_full_context + reasoner.format_analysis)
4. 发给 LLM 生成 JSON 操作 (DeepSeek json_object 模式)

失败降级:
- LLM 不可用 / 解析失败 / 目标非法 → 回退到 RoleStrategy 规则决策
"""
import json
import time
import logging
from typing import TYPE_CHECKING, Dict, Any, List, Optional

from prompts.action_prompts import NIGHT_ACTION_PROMPT, VOTE_PROMPT
from models.game_models import NightActionDecision, VoteDecision, Role, GamePhase
from services.log_service import log_llm_call

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent
    from models.game_models import GameState
    from services.rag_service import RAGService

logger = logging.getLogger(__name__)


# 角色 → 期望的夜间 action 类型
ROLE_EXPECTED_ACTION: Dict[str, str] = {
    "WEREWOLF": "kill",
    "SEER": "check",
    "WITCH": "save/poison/skip",  # 女巫需要 LLM 在 save/poison/skip 三选一
    "GUARD": "guard",
    "HUNTER": "skip",          # 猎人夜晚不主动行动
    "VILLAGER": "skip",
}


class ActionPlanner:
    """LLM 驱动的行动规划器"""

    def __init__(self, llm, json_llm, rag_service: Optional["RAGService"] = None):
        """
        Args:
            llm: 普通 LLM (目前未用, 保留扩展)
            json_llm: 开启 response_format=json_object 的 LLM
            rag_service: RAG 服务 (可选, 无可用时降级为空上下文)
        """
        self.llm = llm
        self.json_llm = json_llm
        self.rag_service = rag_service

    # ========== 夜间行动 ==========
    async def decide_night_action(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> NightActionDecision:
        """
        LLM 驱动的夜间行动决策

        失败 (LLM 异常 / JSON 解析失败 / 非法目标) 则降级到规则策略。
        """
        try:
            prompt_vars = self._build_night_vars(agent, game_state)
            chain = NIGHT_ACTION_PROMPT | self.json_llm

            formatted_prompt = NIGHT_ACTION_PROMPT.format(**prompt_vars)
            start = time.time()
            msg = await chain.ainvoke(prompt_vars)
            duration_ms = (time.time() - start) * 1000

            raw = msg.content if hasattr(msg, "content") else str(msg)
            log_llm_call(
                game_id=agent.game_id,
                player_id=agent.player_id,
                action="night_action",
                prompt=formatted_prompt,
                response=raw,
                duration_ms=duration_ms,
            )

            decision = self._parse_night_json(raw, agent, game_state)
            if decision is not None:
                logger.info(
                    f"[ActionPlanner] Player {agent.player_id} night LLM OK "
                    f"({duration_ms:.0f}ms): target={decision.target_id}, "
                    f"reason={decision.reason}"
                )
                return decision

            logger.warning(f"[ActionPlanner] Player {agent.player_id} LLM 输出非法, 降级规则")
        except Exception as e:
            logger.warning(f"[ActionPlanner] Player {agent.player_id} LLM 夜间决策失败: {e}, 降级规则")

        # 降级: 规则
        return agent.strategy.plan_night_action(agent, game_state)

    # ========== 投票 ==========
    async def decide_vote(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> VoteDecision:
        """LLM 驱动的投票决策, 失败降级规则。"""
        try:
            prompt_vars = self._build_vote_vars(agent, game_state)
            chain = VOTE_PROMPT | self.json_llm

            formatted_prompt = VOTE_PROMPT.format(**prompt_vars)
            start = time.time()
            msg = await chain.ainvoke(prompt_vars)
            duration_ms = (time.time() - start) * 1000

            raw = msg.content if hasattr(msg, "content") else str(msg)
            log_llm_call(
                game_id=agent.game_id,
                player_id=agent.player_id,
                action="vote",
                prompt=formatted_prompt,
                response=raw,
                duration_ms=duration_ms,
            )

            decision = self._parse_vote_json(raw, agent, game_state)
            if decision is not None:
                logger.info(
                    f"[ActionPlanner] Player {agent.player_id} vote LLM OK "
                    f"({duration_ms:.0f}ms): target={decision.target_id}, "
                    f"reason={decision.reason}"
                )
                return decision

            logger.warning(f"[ActionPlanner] Player {agent.player_id} 投票 LLM 输出非法, 降级规则")
        except Exception as e:
            logger.warning(f"[ActionPlanner] Player {agent.player_id} LLM 投票决策失败: {e}, 降级规则")

        return agent.strategy.plan_vote(agent, game_state)

    # ========== Prompt 变量构建 ==========
    def _build_night_vars(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> Dict[str, Any]:
        base = self._build_common_vars(agent, game_state, scene="night")

        # 可选目标: 存活 & 非自己; 狼人还要排除队友
        exclude = {agent.player_id}
        if agent.role == Role.WEREWOLF:
            exclude.update(getattr(agent, "teammates", []) or [])
        targets = [p for p in game_state.alive_players if p not in exclude]
        base["available_targets"] = ", ".join(f"{p}号" for p in targets) if targets else "无"
        base["expected_action"] = ROLE_EXPECTED_ACTION.get(agent.role.value, "skip")
        return base

    def _build_vote_vars(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> Dict[str, Any]:
        base = self._build_common_vars(agent, game_state, scene="vote")

        targets = [p for p in game_state.alive_players if p != agent.player_id]
        base["available_targets"] = ", ".join(f"{p}号" for p in targets) if targets else "无"
        return base

    def _build_common_vars(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
        scene: str,
    ) -> Dict[str, Any]:
        # ① 角色 Prompt
        role_prompt = agent.strategy.get_system_prompt(agent)
        persona_desc = agent.persona_profile.get_prompt_description()

        # ② RAG 检索
        rag_context = self._retrieve_rag(agent, game_state, scene)

        # ③ 记忆 + 推理
        memory_ctx = agent.memory.get_full_context()
        reasoning = agent.reasoner.format_analysis(game_state.alive_players)

        alive_str = ", ".join(f"{p}号" for p in game_state.alive_players) or "无"
        deaths_str = memory_ctx.get("deaths") or "无"

        return {
            "seat_number": agent.seat_number or agent.player_id,
            "role_prompt": role_prompt,
            "persona_description": persona_desc,
            "round": game_state.round,
            "phase": game_state.phase.value if isinstance(game_state.phase, GamePhase) else str(game_state.phase),
            "alive_players": alive_str,
            "deaths": deaths_str,
            "timeline": memory_ctx.get("timeline") or "(暂无记忆)",
            "reasoning": reasoning or "(暂无推理)",
            "rag_context": rag_context,
        }

    def _retrieve_rag(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
        scene: str,
    ) -> str:
        """RAG 检索策略知识, 失败则返回占位文本。"""
        if not (self.rag_service and self.rag_service.is_available()):
            return "(RAG 不可用)"

        try:
            role_name = agent.strategy.role_name
            if scene == "night":
                query = f"{role_name}在{game_state.phase.value}阶段应该如何行动和选择目标"
            else:
                query = f"{role_name}在投票阶段如何判断嫌疑和选择投票目标"

            docs = self.rag_service.query(query, role_filter=agent.role.value, k=2)
            if not docs:
                # 不带角色过滤再试一次
                docs = self.rag_service.query(query, role_filter=None, k=2)

            if docs:
                return self.rag_service.format_docs(docs)
            return "(未检索到相关策略)"
        except Exception as e:
            logger.warning(f"[ActionPlanner] RAG 检索失败: {e}")
            return "(RAG 检索异常)"

    # ========== JSON 解析 + 合法性校验 ==========
    def _parse_night_json(
        self,
        raw: str,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> Optional[NightActionDecision]:
        data = self._safe_json_loads(raw)
        if not isinstance(data, dict):
            return None

        try:
            target_id = int(data.get("target_id", 0) or 0)
        except (TypeError, ValueError):
            return None

        reason = str(data.get("reason") or "").strip() or "LLM 未给出理由"
        try:
            confidence = float(data.get("confidence", 0.6))
        except (TypeError, ValueError):
            confidence = 0.6
        confidence = max(0.0, min(1.0, confidence))

        action = str(data.get("action") or ROLE_EXPECTED_ACTION.get(agent.role.value, "skip"))

        # 女巫特殊处理: save/poison/skip
        if agent.role == Role.WITCH:
            action = action.lower().strip()
            if action not in ("save", "poison", "skip"):
                logger.warning(f"[ActionPlanner] 女巫 LLM 返回非法 action={action}, 降级 skip")
                return NightActionDecision(
                    action="skip", target_id=0, reason=reason, confidence=confidence
                )
            if action == "save":
                # 救今晚被杀者, target_id 由 Java 侧处理, 这里统一填 0
                return NightActionDecision(
                    action="save", target_id=0, reason=reason, confidence=confidence
                )
            if action == "skip":
                return NightActionDecision(
                    action="skip", target_id=0, reason=reason, confidence=confidence
                )
            # poison: 必须有合法的存活目标
            if target_id == 0 or target_id not in game_state.alive_players:
                logger.warning(f"[ActionPlanner] 女巫毒药目标非法: {target_id}, 降级 skip")
                return NightActionDecision(
                    action="skip", target_id=0, reason=reason, confidence=confidence
                )
            if target_id == agent.player_id:
                logger.warning(f"[ActionPlanner] 女巫毒药不能选自己, 降级 skip")
                return NightActionDecision(
                    action="skip", target_id=0, reason=reason, confidence=confidence
                )
            return NightActionDecision(
                action="poison", target_id=target_id, reason=reason, confidence=confidence
            )

        # 其他角色: target_id = 0 表示不行动, 合法
        if target_id == 0:
            return NightActionDecision(
                action=action, target_id=0, reason=reason, confidence=confidence
            )

        # 非法校验: 必须在存活列表, 不能是自己, 狼人不能选队友
        if target_id not in game_state.alive_players:
            logger.warning(f"[ActionPlanner] LLM 选了非存活目标 {target_id}, 拒绝")
            return None
        if target_id == agent.player_id:
            logger.warning(f"[ActionPlanner] LLM 选了自己 {target_id}, 拒绝")
            return None
        if agent.role == Role.WEREWOLF and target_id in (getattr(agent, "teammates", []) or []):
            logger.warning(f"[ActionPlanner] 狼人 LLM 选了队友 {target_id}, 拒绝")
            return None

        return NightActionDecision(
            action=action, target_id=target_id, reason=reason, confidence=confidence
        )

    def _parse_vote_json(
        self,
        raw: str,
        agent: "WerewolfAgent",
        game_state: "GameState",
    ) -> Optional[VoteDecision]:
        data = self._safe_json_loads(raw)
        if not isinstance(data, dict):
            return None

        try:
            target_id = int(data.get("target_id", 0) or 0)
        except (TypeError, ValueError):
            return None

        reason = str(data.get("reason") or "").strip() or "LLM 未给出理由"
        try:
            confidence = float(data.get("confidence", 0.5))
        except (TypeError, ValueError):
            confidence = 0.5
        confidence = max(0.0, min(1.0, confidence))

        # 0 = 弃票, 合法
        if target_id == 0:
            return VoteDecision(target_id=0, reason=reason, confidence=confidence)

        if target_id not in game_state.alive_players:
            logger.warning(f"[ActionPlanner] 投票 LLM 选了非存活目标 {target_id}, 拒绝")
            return None
        if target_id == agent.player_id:
            logger.warning(f"[ActionPlanner] 投票 LLM 选了自己 {target_id}, 拒绝")
            return None

        return VoteDecision(target_id=target_id, reason=reason, confidence=confidence)

    @staticmethod
    def _safe_json_loads(raw: str) -> Any:
        """容忍 LLM 可能输出的 markdown 代码块包裹。"""
        if not raw:
            return None
        text = raw.strip()
        # 剥离 ```json ... ``` / ``` ... ```
        if text.startswith("```"):
            text = text.strip("`")
            # 去掉可能的语言标识行 (json)
            if "\n" in text:
                first, rest = text.split("\n", 1)
                if first.strip().lower() in ("json", ""):
                    text = rest
            text = text.strip().rstrip("`").strip()

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            # 尝试截取第一个 { ... } 片段
            l = text.find("{")
            r = text.rfind("}")
            if l >= 0 and r > l:
                try:
                    return json.loads(text[l:r + 1])
                except json.JSONDecodeError:
                    return None
            return None
