"""
记忆系统 (MemorySystem)

统一管理三层记忆:
- WorkingMemory: 工作记忆
- EpisodicMemory: 情景记忆
- SemanticMemory: 语义记忆

负责:
- 事件分发到各层记忆
- 提供统一的上下文查询接口
- 记忆整合与格式化
"""
from typing import List, Optional, Dict, Any
import logging

from agents.memory.working_memory import WorkingMemory
from agents.memory.episodic_memory import EpisodicMemory
from agents.memory.semantic_memory import SemanticMemory
from models.event_models import GameEvent, EventType

logger = logging.getLogger(__name__)


class MemorySystem:
    """统一记忆管理系统"""
    
    def __init__(self, my_player_id: int):
        self.my_player_id = my_player_id
        self.working = WorkingMemory()
        self.episodic = EpisodicMemory()
        self.semantic = SemanticMemory(my_player_id)
    
    def init_game(self, player_ids: List[int], seat_map: Optional[Dict[int, int]] = None):
        """游戏开始时初始化"""
        self.semantic.init_players(player_ids, seat_map)
        logger.info(f"Memory initialized for {len(player_ids)} players")
    
    def process_event(self, event: GameEvent):
        """
        处理游戏事件，分发到各层记忆
        
        这是记忆系统的核心入口，所有事件都通过这里处理。
        """
        et = event.event_type
        data = event.event_data
        round_num = event.round
        phase = event.phase
        
        # 更新工作记忆的阶段信息
        self.working.update_phase(round_num, phase)
        self.working.add_event(et.value, data)
        
        # 按事件类型分发处理
        if et == EventType.PHASE_CHANGE:
            self._handle_phase_change(round_num, data)
        
        elif et == EventType.PLAYER_DIED:
            self._handle_death(round_num, data)
        
        elif et == EventType.PLAYER_SPEECH:
            self._handle_speech(round_num, phase, data)
        
        elif et == EventType.VOTE_RESULT:
            self._handle_vote_result(round_num, data)
        
        elif et == EventType.SEER_CHECK_RESULT:
            self._handle_check_result(round_num, data)
        
        elif et == EventType.WITCH_SAVE_USED:
            self._handle_witch_save(round_num, data)
        
        elif et == EventType.WITCH_POISON_USED:
            self._handle_witch_poison(round_num, data)
        
        elif et == EventType.HUNTER_SHOOT:
            self._handle_hunter_shoot(round_num, data)
        
        elif et == EventType.GAME_START:
            self._handle_game_start(data)
    
    def _handle_phase_change(self, round_num: int, data: Dict):
        """处理阶段变化"""
        new_phase = data.get("phase", "")
        self.working.update_phase(round_num, new_phase)
    
    def _handle_death(self, round_num: int, data: Dict):
        """处理死亡事件"""
        player_id = data.get("player_id")
        cause = data.get("cause", "unknown")
        
        if player_id:
            self.working.add_death(player_id)
            self.episodic.record_death(round_num, player_id, cause)
            
            profile = self.semantic.get_or_create(player_id)
            profile.mark_dead(round_num, cause)
    
    def _handle_speech(self, round_num: int, phase: str, data: Dict):
        """处理发言事件"""
        speaker_id = data.get("player_id") or data.get("speaker_id")
        content = data.get("content", "")
        
        if speaker_id and content:
            self.working.add_speech(speaker_id, content, round_num)
            self.episodic.add_episode(
                round=round_num, phase=phase, event_type="SPEECH",
                actor_id=speaker_id, content=content, importance=0.4
            )
            # 更新玩家画像的发言摘要
            profile = self.semantic.get_or_create(speaker_id)
            summary = content[:80] + ("..." if len(content) > 80 else "")
            profile.add_speech_summary(f"第{round_num}天: {summary}")
    
    def _handle_vote_result(self, round_num: int, data: Dict):
        """处理投票结果"""
        votes = data.get("votes", {})
        result = data.get("eliminated_player")
        
        # 记录每个人的投票
        int_votes = {}
        for voter, target in votes.items():
            voter_id = int(voter) if isinstance(voter, str) else voter
            target_id = int(target) if isinstance(target, str) else target
            int_votes[voter_id] = target_id
            self.working.add_vote(voter_id, target_id)
            
            # 更新玩家画像
            profile = self.semantic.get_or_create(voter_id)
            profile.add_vote(round_num, target_id)
        
        self.episodic.record_vote_round(round_num, int_votes, result)
        
        # 分析投票关系
        self._analyze_vote_relationships(int_votes)
    
    def _handle_check_result(self, round_num: int, data: Dict):
        """处理查验结果（仅预言家自己收到）"""
        target_id = data.get("target_id")
        result = data.get("result", "")  # "好人" 或 "狼人"
        
        if target_id:
            self.working.set_flag(f"check_{target_id}", result)
            self.episodic.add_episode(
                round=round_num, phase="SEER", event_type="CHECK_RESULT",
                target_id=target_id, content=result, importance=1.0
            )
            
            # 更新语义记忆
            if "狼" in result or result == "WEREWOLF":
                self.semantic.set_known_role(target_id, "WEREWOLF")
            else:
                self.semantic.set_known_role(target_id, "GOOD")
    
    def _handle_witch_save(self, round_num: int, data: Dict):
        """处理女巫救人（仅女巫自己收到）"""
        target_id = data.get("target_id")
        if target_id:
            self.working.set_flag("witch_saved", target_id)
            self.episodic.add_episode(
                round=round_num, phase="WITCH", event_type="SAVE",
                target_id=target_id, importance=0.8
            )
    
    def _handle_witch_poison(self, round_num: int, data: Dict):
        """处理女巫毒人（仅女巫自己收到）"""
        target_id = data.get("target_id")
        if target_id:
            self.working.set_flag("witch_poisoned", target_id)
            self.episodic.add_episode(
                round=round_num, phase="WITCH", event_type="POISON",
                target_id=target_id, importance=0.8
            )
    
    def _handle_hunter_shoot(self, round_num: int, data: Dict):
        """处理猎人开枪"""
        shooter_id = data.get("player_id")
        target_id = data.get("target_id")
        if target_id:
            self.episodic.add_episode(
                round=round_num, phase="HUNTER", event_type="HUNTER_SHOOT",
                actor_id=shooter_id, target_id=target_id, importance=0.9
            )
    
    def _handle_game_start(self, data: Dict):
        """处理游戏开始"""
        players = data.get("players", [])
        seat_map = data.get("seat_map", {})
        if players:
            player_ids = [p if isinstance(p, int) else p.get("player_id") for p in players]
            self.semantic.init_players(player_ids, seat_map)
    
    def _analyze_vote_relationships(self, votes: Dict[int, int]):
        """分析投票关系，更新关系图"""
        voter_list = list(votes.keys())
        for i, voter_a in enumerate(voter_list):
            target_a = votes[voter_a]
            for voter_b in voter_list[i + 1:]:
                target_b = votes[voter_b]
                
                if target_a == target_b:
                    # 投同一个人 → 关系更近
                    self.semantic.update_relationship(voter_a, voter_b, 0.1)
                
                if target_a == voter_b and target_b == voter_a:
                    # 互投 → 关系更对立
                    self.semantic.update_relationship(voter_a, voter_b, -0.2)
    
    def get_full_context(self) -> Dict[str, str]:
        """
        获取完整记忆上下文（用于注入 Prompt）
        
        Returns:
            包含各层记忆格式化文本的字典
        """
        return {
            "working_context": self.working.format_context(),
            "timeline": self.episodic.format_timeline(),
            "deaths": self.episodic.format_deaths(),
            "player_profiles": self.semantic.format_profiles(),
            "suspicion_ranking": self.semantic.format_suspicion_ranking(),
        }
    
    def get_info(self) -> Dict:
        """获取记忆系统状态信息"""
        return {
            "working_memory": {
                "current_round": self.working.current_round,
                "current_phase": self.working.current_phase,
                "round_events_count": len(self.working.round_events),
                "recent_speeches_count": len(self.working.recent_speeches),
            },
            "episodic_memory": {
                "total_episodes": len(self.episodic.episodes),
                "deaths_count": len(self.episodic.deaths_timeline),
                "vote_rounds": len(self.episodic.vote_history),
            },
            "semantic_memory": {
                "tracked_players": len(self.semantic.profiles),
                "relationships": len(self.semantic.relationships),
            }
        }
