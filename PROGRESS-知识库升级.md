# 知识库升级进度

> 基于桌面文档攻略的项目优化实施记录
> 开始时间：2026-05-04 20:38

## 任务清单

| # | 任务 | 状态 | 修改文件 | 提交 |
|---|------|------|---------|------|
| A1 | 新增3个知识库文档 | ✅ 完成 | `knowledge/10-警长竞选与警徽流.md`, `11-发言模板与实战案例.md`, `12-狼队战术分工详解.md` | commit 1 |
| A2 | 增强现有4个知识库文档 | ✅ 完成 | `knowledge/01,02,05,07-*.md` | commit 2 |
| C1 | 增强各角色系统提示词 | ✅ 完成 | `strategies/*.py` (6个文件) | commit 3 |
| B2 | 发言模板注入 SpeechGenerator | ✅ 完成 | `base_strategy.py`, `seer/witch/werewolf_strategy.py`, `speech_generator.py` | commit 4 |
| A3 | RAG 元数据优化 | ✅ 完成 | `services/rag_service.py` | commit 5 |

## 变更记录

### [A1] 新增3个知识库文档 — 2026-05-04 20:48
**新增文件：**
- `packages/ai-service/knowledge/10-警长竞选与警徽流.md` — 警长竞选流程、警徽流1.0/2.0版详解、各角色上警策略、狼人上警战术（一狼~四狼+零狼5种）
- `packages/ai-service/knowledge/11-发言模板与实战案例.md` — 预言家/女巫/猎人/村民/狼人各角色可直接使用的发言模板、实战对局逐轮发言记录、轮次思维
- `packages/ai-service/knowledge/12-狼队战术分工详解.md` — 悍跳狼/冲锋狼/深水狼/倒钩狼分工、狼队配合战术、狼查杀狼/穿衣服/自刀骗药/爆刀节奏等进阶战术、白狼王特殊战术

### [A2] 增强现有4个知识库文档 — 2026-05-04 20:55
**修改文件：**
- `01-游戏规则.md` — 补充12人标准局配置、警上竞选流程、遗言规则细节、女巫不能自救修正、白痴翻牌规则、白狼王规则、狼人自刀规则
- `02-角色技能.md` — 狼人增加自刀/战术分工、预言家增加警徽流概念、女巫增加身份隐藏技巧、猎人增加隐藏策略、村民增加挡刀/诈身份建议
- `05-狼人策略.md` — 追加5种上警战术详解（一狼~四狼+零狼上警）、白狼王策略（目标优先级/时机选择）
- `07-女巫策略.md` — 追加守救守配合策略、身份隐藏技巧（躲刀/发言/银水时机）、解药使用后/未使用的不同策略

### [C1] 增强各角色系统提示词 — 2026-05-04 21:05
**修改文件（6个策略文件的 get_system_prompt 方法）：**
- `seer_strategy.py` — 补充警徽流策略、上警要求、对跳处理、发言语气要求
- `witch_strategy.py` — 补充身份隐藏技巧、躲刀策略、守救守配合、毒药使用原则
- `hunter_strategy.py` — 补充隐藏策略、带队时机、被查杀时的3种应对方式
- `guard_strategy.py` — 补充守救守节奏、同守同救注意、博心态策略、隐藏身份要点
- `villager_strategy.py` — 补充挡刀意识、穿衣服帮神挡刀、投票重要性、心态建设
- `werewolf_strategy.py` — 补充心理建设（把自己当神牌）、战术分工参考、拉票策略、倒钩提示

### [B2] 发言模板注入 SpeechGenerator — 2026-05-04 21:10
**修改文件：**
- `base_strategy.py` — 新增 `get_speech_example()` 抽象方法，提供角色发言模板 few-shot 示例
- `seer_strategy.py` — 新增预言家上警发言模板（含警徽流留法）
- `witch_strategy.py` — 新增女巫跳身份/隐藏身份两种发言模板
- `werewolf_strategy.py` — 新增假装平民/悍跳预言家发言模板+狼队暗号技巧
- `speech_generator.py` — 在 `_build_prompt_variables` 中将角色发言示例作为 few-shot 注入 reasoning_context

### [A3] RAG 元数据优化 — 2026-05-04 21:12
**修改文件：**
- `services/rag_service.py` — `_enrich_metadata()` 方法增强：
  - 角色识别：新增"狼队"关键词匹配 WEREWOLF
  - 文档类型：新增 sheriff（警徽）、case_study（实战案例）
  - 游戏阶段（新维度）：sheriff_election / night / vote / discussion
  - 难度等级（新维度）：beginner / advanced（根据文件名和内容关键词推断）

### [D1] 混合检索 + Rerank + Query 改写 — 2026-05-27

将单路向量召回升级为**多阶段检索管线**，覆盖业界主流 RAG 优化方向。

#### 1) 混合检索（Hybrid Retrieval）
**新增文件：** `services/hybrid_retriever.py`
- **稠密向量检索**：保留 Chroma + BGE Embedding（语义相似强项）
- **稀疏 BM25 检索**：基于 `rank_bm25` + `jieba` 中文分词，捕获专有名词/术语精确命中（"警徽流2.0"/"悍跳"/"倒钩"等）
- **RRF 融合**：`score_rrf(d) = Σ 1/(60 + rank_r(d))`，业界经典无参融合方案，对各路打分尺度不敏感
- 默认开启 (`USE_HYBRID_RETRIEVAL=true`)，BM25 语料从 Chroma 中按需懒构建

#### 2) Cross-Encoder 精排
**新增文件：** `services/reranker_service.py`
- 模型：`BAAI/bge-reranker-base`（中文专用 Cross-Encoder）
- 范式：召回多 (`recall_k=20`) → 精排少 (`top_k=3~5`)
- 输出 logit 经 sigmoid 归一化到 [0,1]
- 模型懒加载（首次调用才下载 ~280MB），CPU 推理约 700ms / 20 候选
- 默认 admin 调试器手动开启；agent 实时决策默认 `USE_RERANKER=false`，避免拖慢游戏节奏

#### 3) Query 改写
**新增文件：** `services/query_rewriter.py`
- **HyDE**（Hypothetical Document Embeddings, Gao et al. ACL 2023）：让 LLM 先写一段假设性回答，用伪文档去检索，弥补 query-doc 语义鸿沟
- **Multi-Query**：让 LLM 生成 N 个不同角度的改写 query，并行检索后合并候选
- 两者均通过环境变量按需开启（默认 `none`），需要额外一次 LLM 调用（~5s）

#### 4) RAG 服务重构
**修改文件：** `services/rag_service.py`
- 新增 `aquery_pipeline()` / `query_pipeline_sync()` 异步/同步管线入口
- 返回 `stages: List[Dict]`，记录每一步的耗时、候选数、模型名
- 检索日志 `extra` 字段新增 `pipeline` 和 `stages`，可在日志页查看完整追踪
- 旧 API（`query / aquery / query_with_scores`）完全向后兼容

#### 5) Admin UI 升级
**修改文件：** `routers/admin.py`, `admin-ui/src/views/SearchPlayground.vue`, `SystemStatus.vue`
- `/search` 接口新增 `use_hybrid / use_reranker / query_rewrite / recall_k` 参数
- 调试器 UI 加入 4 个开关，支持实时对比不同 pipeline 组合的效果
- **流水线可视化**：每次检索展示完整 stages（query 改写 → 双路召回 → RRF 融合 → Rerank），含每步耗时色阶（绿/黄/红）
- 系统状态页流程图升级为 8 步完整管线

#### 性能基准（24 chunks 知识库，本地 CPU）
| 模式 | 总耗时 (缓存后) | 备注 |
|---|---:|---|
| 仅向量（baseline） | ~100ms | Chroma similarity_search |
| 混合检索（向量 + BM25 + RRF） | ~10ms | 召回毫秒级，融合可忽略 |
| 混合检索 + Rerank | ~750ms | rerank 是主要瓶颈，20 候选 / CPU |
| 混合检索 + Rerank + HyDE | ~5.7s | HyDE 需一次 DeepSeek 调用 |

#### 新增依赖
```
rank_bm25>=0.2.2
jieba>=0.42.1
```
（reranker 复用已有 `sentence-transformers`）

#### 环境变量
```bash
USE_HYBRID_RETRIEVAL=true       # 默认 true
USE_RERANKER=false              # 默认 false（agent 实时调用关闭，admin 调试可手动开）
USE_QUERY_REWRITE=none          # none / hyde / multi_query
RERANK_RECALL_K=20              # 召回阶段每路保留数
RERANKER_MODEL=BAAI/bge-reranker-base
```
