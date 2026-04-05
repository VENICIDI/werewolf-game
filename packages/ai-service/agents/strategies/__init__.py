"""
角色策略模块

策略模式: 每种角色有独立的策略实现
"""
from agents.strategies.base_strategy import RoleStrategy
from agents.strategies.werewolf_strategy import WerewolfStrategy
from agents.strategies.seer_strategy import SeerStrategy
from agents.strategies.witch_strategy import WitchStrategy
from agents.strategies.hunter_strategy import HunterStrategy
from agents.strategies.guard_strategy import GuardStrategy
from agents.strategies.villager_strategy import VillagerStrategy

STRATEGY_MAP = {
    "WEREWOLF": WerewolfStrategy,
    "SEER": SeerStrategy,
    "WITCH": WitchStrategy,
    "HUNTER": HunterStrategy,
    "GUARD": GuardStrategy,
    "VILLAGER": VillagerStrategy,
}


def create_strategy(role: str) -> RoleStrategy:
    """根据角色创建对应策略"""
    cls = STRATEGY_MAP.get(role, VillagerStrategy)
    return cls()


__all__ = [
    "RoleStrategy", "create_strategy", "STRATEGY_MAP",
    "WerewolfStrategy", "SeerStrategy", "WitchStrategy",
    "HunterStrategy", "GuardStrategy", "VillagerStrategy",
]
