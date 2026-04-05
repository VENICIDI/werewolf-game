"""
Agent 端到端测试

测试完整流程: 创建Agent → 推送事件 → 夜间决策 → 发言生成 → 投票决策

使用方式:
  cd packages/ai-service
  python -m pytest tests/test_agent_e2e.py -v
  
  或直接运行:
  python tests/test_agent_e2e.py
"""
import asyncio
import sys
import os

# 添加项目根目录到 path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

from models.game_models import Role, GameState, GamePhase, PlayerInfo
from models.agent_models import Persona
from models.event_models import GameEvent, EventType
from agents.base_agent import WerewolfAgent
from agents.agent_manager import AgentManager


# ============ 测试工具 ============

def make_game_state(round_num=1, phase=GamePhase.WEREWOLF, alive=None, dead=None):
    """创建测试用 GameState"""
    alive = alive or [1, 2, 3, 4, 5, 6, 7, 8, 9]
    dead = dead or []
    player_infos = {
        pid: PlayerInfo(player_id=pid, is_alive=(pid in alive))
        for pid in alive + dead
    }
    return GameState(
        game_id="test_game_1",
        round=round_num,
        phase=phase,
        alive_players=alive,
        dead_players=dead,
        player_infos=player_infos,
    )


def make_event(event_type, data, round_num=1, phase="NIGHT"):
    """创建测试用 GameEvent"""
    return GameEvent(
        game_id="test_game_1",
        player_id=0,
        event_type=event_type,
        event_data=data,
        round=round_num,
        phase=phase,
    )


# ============ 测试用例 ============

async def test_werewolf_agent():
    """测试狼人 Agent 完整流程"""
    print("\n===== 测试: 狼人 Agent =====")
    
    agent = WerewolfAgent(
        game_id="test_game_1",
        player_id=1,
        role=Role.WEREWOLF,
        persona=Persona.AGGRESSIVE,
        llm_service=None,
        rag_service=None,
        teammates=[2, 3],
        seat_number=1,
    )
    agent.init_game([1, 2, 3, 4, 5, 6, 7, 8, 9])
    
    # 1. 夜间决策
    gs = make_game_state(round_num=1, phase=GamePhase.WEREWOLF)
    decision = await agent.decide_night_action(gs)
    print(f"  夜间决策: target={decision.target_id}, reason={decision.reason}")
    assert decision.target_id not in [1, 2, 3], "狼人不应刀自己或队友"
    assert decision.target_id in [4, 5, 6, 7, 8, 9], "目标应在可选范围内"
    
    # 2. 发言
    gs_day = make_game_state(round_num=1, phase=GamePhase.DISCUSSION, alive=[1,2,3,4,5,6,7,8], dead=[9])
    speech = await agent.generate_speech(gs_day, "discussion")
    print(f"  发言: {speech.content[:60]}...")
    assert len(speech.content) > 0, "发言不应为空"
    
    # 3. 投票
    gs_vote = make_game_state(round_num=1, phase=GamePhase.VOTING, alive=[1,2,3,4,5,6,7,8], dead=[9])
    vote = await agent.decide_vote(gs_vote)
    print(f"  投票: target={vote.target_id}, reason={vote.reason}")
    assert vote.target_id not in [1, 2, 3], "狼人不应投队友"
    
    print("  ✅ 狼人测试通过")


async def test_seer_agent():
    """测试预言家 Agent"""
    print("\n===== 测试: 预言家 Agent =====")
    
    agent = WerewolfAgent(
        game_id="test_game_1",
        player_id=4,
        role=Role.SEER,
        persona=Persona.ANALYTICAL,
        llm_service=None,
        rag_service=None,
        seat_number=4,
    )
    agent.init_game([1, 2, 3, 4, 5, 6, 7, 8, 9])
    
    # 1. 夜间查验
    gs = make_game_state(round_num=1, phase=GamePhase.SEER)
    decision = await agent.decide_night_action(gs)
    print(f"  查验决策: target={decision.target_id}, reason={decision.reason}")
    assert decision.target_id != 4, "预言家不应查验自己"
    assert decision.target_id in range(1, 10), "目标应在玩家范围内"
    
    # 2. 接收查验结果
    check_event = make_event(
        EventType.SEER_CHECK_RESULT,
        {"target_id": 2, "result": "狼人"},
        round_num=1, phase="SEER"
    )
    await agent.receive_event(check_event)
    
    # 验证推理引擎已更新
    assert agent.reasoner.posteriors.get(2, 0) > 0.9, "查杀后嫌疑值应接近1"
    
    # 3. 投票（应投已查杀的狼人）
    gs_vote = make_game_state(round_num=1, phase=GamePhase.VOTING)
    vote = await agent.decide_vote(gs_vote)
    print(f"  投票: target={vote.target_id}, reason={vote.reason}")
    assert vote.target_id == 2, "预言家应投已查杀的狼人"
    
    print("  ✅ 预言家测试通过")


async def test_witch_agent():
    """测试女巫 Agent"""
    print("\n===== 测试: 女巫 Agent =====")
    
    agent = WerewolfAgent(
        game_id="test_game_1",
        player_id=5,
        role=Role.WITCH,
        persona=Persona.CAUTIOUS,
        llm_service=None,
        rag_service=None,
        seat_number=5,
    )
    agent.init_game([1, 2, 3, 4, 5, 6, 7, 8, 9])
    
    # 设置被刀信息
    agent.memory.working.set_flag("tonight_killed", 4)
    
    # 夜间决策（第一晚应该救人）
    gs = make_game_state(round_num=1, phase=GamePhase.WITCH)
    decision = await agent.decide_night_action(gs)
    print(f"  女巫决策: target={decision.target_id}, reason={decision.reason}")
    assert decision.target_id == 4, "第一晚女巫应救被刀的人"
    
    print("  ✅ 女巫测试通过")


async def test_memory_system():
    """测试记忆系统"""
    print("\n===== 测试: 记忆系统 =====")
    
    agent = WerewolfAgent(
        game_id="test_game_1",
        player_id=6,
        role=Role.VILLAGER,
        persona=Persona.CHARMING,
        llm_service=None,
        rag_service=None,
        seat_number=6,
    )
    agent.init_game([1, 2, 3, 4, 5, 6, 7, 8, 9])
    
    # 推送死亡事件
    death_event = make_event(
        EventType.PLAYER_DIED,
        {"player_id": 3, "cause": "killed"},
        round_num=1, phase="DAY_START"
    )
    await agent.receive_event(death_event)
    
    # 验证情景记忆
    assert len(agent.memory.episodic.deaths_timeline) == 1
    assert agent.memory.episodic.deaths_timeline[0]["player_id"] == 3
    
    # 推送发言事件
    speech_event = make_event(
        EventType.PLAYER_SPEECH,
        {"player_id": 1, "content": "我是预言家，昨晚查了2号是狼人！"},
        round_num=1, phase="DISCUSSION"
    )
    await agent.receive_event(speech_event)
    
    # 验证语义记忆检测到身份声明
    profile_1 = agent.memory.semantic.get_profile(1)
    assert profile_1.claimed_role == "SEER", "应检测到1号声称预言家"
    
    # 验证记忆信息
    info = agent.memory.get_info()
    print(f"  记忆状态: {info}")
    assert info["episodic_memory"]["deaths_count"] == 1
    assert info["episodic_memory"]["total_episodes"] >= 1
    
    print("  ✅ 记忆系统测试通过")


async def test_bayesian_reasoning():
    """测试贝叶斯推理"""
    print("\n===== 测试: 贝叶斯推理 =====")
    
    from agents.reasoning.bayesian_reasoner import BayesianReasoner
    
    reasoner = BayesianReasoner(my_player_id=1, total_players=9, wolf_count=3)
    reasoner.init_players([1, 2, 3, 4, 5, 6, 7, 8, 9])
    
    # 初始概率
    initial = reasoner.posteriors.get(2, 0)
    print(f"  初始嫌疑 P(2号=狼): {initial:.3f}")
    assert abs(initial - 3/9) < 0.01, "初始嫌疑应为 wolf_count/total"
    
    # 添加查杀证据
    reasoner.add_evidence(round=1, evidence_type="seer_kill", target_id=2, description="预言家查杀")
    after_kill = reasoner.posteriors.get(2, 0)
    print(f"  查杀后嫌疑: {after_kill:.3f}")
    assert after_kill > 0.9, "查杀后嫌疑应极高"
    
    # 确认好人
    reasoner.confirm_good(5)
    assert reasoner.posteriors.get(5, 0) < 0.05, "确认好人后嫌疑应极低"
    
    # 排名
    ranking = reasoner.get_ranking()
    print(f"  嫌疑排名: {ranking[:3]}")
    assert ranking[0][0] == 2, "2号应排第一"
    
    print("  ✅ 贝叶斯推理测试通过")


async def test_agent_manager():
    """测试 AgentManager"""
    print("\n===== 测试: AgentManager =====")
    
    manager = AgentManager.__new__(AgentManager)
    manager._agents = {}
    manager._lock = asyncio.Lock()
    manager._llm_service = None
    manager._rag_service = None
    manager._initialized = True
    
    # 创建
    agent = await manager.create_agent(
        game_id="test_1",
        player_id=1,
        role=Role.WEREWOLF,
        persona=Persona.AGGRESSIVE,
        teammates=[2],
        player_ids=[1, 2, 3, 4, 5],
    )
    assert agent is not None
    print(f"  创建: {agent.get_info()['role']}")
    
    # 查询
    found = await manager.get_agent("test_1", 1)
    assert found is not None
    
    # 统计
    stats = manager.get_stats()
    assert stats["total_agents"] == 1
    print(f"  统计: {stats}")
    
    # 销毁
    ok = await manager.destroy_agent("test_1", 1)
    assert ok is True
    assert manager.get_stats()["total_agents"] == 0
    
    print("  ✅ AgentManager 测试通过")


# ============ 运行 ============

async def run_all():
    """运行所有测试"""
    print("🧪 开始端到端测试...\n")
    
    await test_werewolf_agent()
    await test_seer_agent()
    await test_witch_agent()
    await test_memory_system()
    await test_bayesian_reasoning()
    await test_agent_manager()
    
    print("\n🎉 所有测试通过！")


if __name__ == "__main__":
    asyncio.run(run_all())
