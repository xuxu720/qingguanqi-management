# 清管器作业管理系统

油气管道清管作业的全流程管理平台，覆盖清管器档案、管线台账、作业创建与跟踪、节点反馈、预警轮询、预测计算及 AI 智能助手。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite 6 + Element Plus 2 + ECharts 6 |
| 后端 | Spring Boot 3.3 + MyBatis-Plus 3.5 + Knife4j (OpenAPI 3) |
| 数据库 | MySQL 8.0 |
| AI 助手 | DeepSeek API（Chat Completions） |

## 功能模块

### 基础数据管理
- **清管器管理** — 类型、规格、过盈量、适用介质、状态维护
- **管线管理** — 管线名称、介质类型、管径、设计压力、总长度
- **站点管理** — 站场/阀室信息、累计里程、高程、排序
- **管段管理** — 相邻站间距离、单位管容（液体）或内径（气体）

### 清管作业
- **作业创建** — 5 步向导：选管线 → 选收发站 → 标记关键站 → 选清管器 → 填写工况参数
- **作业跟踪** — 节点进度步骤条、跟踪时间线、预测 vs 实际偏差
- **节点反馈** — 关键站到达确认，触发下游预测自动重算（roll forward）
- **管线纵断面图** — ECharts 可视化清管器当前位置、高程剖面

### 预警中心
- 延迟预警、速度异常预警、卡阻预警自动生成
- 仅针对关键站（收发球站、大型跨越、转折点）触发
- 每 10 秒自动刷新，支持暂停/恢复、批量确认/关闭

### 预测计算器
- 液体管道：排量 → 速度 → 各站到达时间
- 气体管道：输气量 + 压力 + 压缩因子查表 → 速度 → 各站到达时间

### AI 智能助手
- 自然语言创建作业、查询状态、节点反馈
- 意图识别 + 参数提取 + 后续追问

## 快速开始

### 环境要求
- **JDK 17+**
- **Maven 3.8+**（或使用项目自带的 `mvnw`）
- **Node.js 20+** 和 npm
- **MySQL 8.0**

### 1. 初始化数据库

```sql
-- 使用 MySQL 客户端或命令行执行
mysql -u root -p < database/init0.sql
```

默认数据库名 `qingguanqi`，用户名/密码均为 `root`（可在 `backend/src/main/resources/application.yml` 中修改）。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问：
- API 服务：`http://localhost:8080`
- Knife4j 接口文档：`http://localhost:8080/doc.html`

### 3. 启动前端

```bash
cd frontend
npm install    # 首次运行需安装依赖
npm run dev
```

前端启动后访问：`http://localhost:5173`

### 一键启动

双击项目根目录下的 `start-all.bat` 即可同时启动前后端。

## 项目结构

```
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/qingguanqi/
│   │   ├── agent/                    # AI 智能助手（DeepSeek 集成）
│   │   ├── config/                   # 跨域、异常处理、MyBatis-Plus、Knife4j 配置
│   │   ├── controller/               # REST 控制器
│   │   ├── dto/                      # 数据传输对象（VO、请求体、通用 Result）
│   │   ├── engine/                   # 液体/气体预测计算引擎
│   │   ├── entity/                   # 数据库实体
│   │   ├── enums/                    # 枚举（状态、类型、等级）
│   │   ├── mapper/                   # MyBatis-Plus Mapper 接口
│   │   ├── scheduler/                # 预警定时任务（每分钟轮询）
│   │   └── service/                  # 业务服务层
│   └── src/main/resources/
│       └── application.yml           # 数据库连接、MyBatis-Plus 配置
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── api/                      # Axios API 封装
│   │   ├── components/               # 共享组件（管线纵断面图等）
│   │   ├── composables/              # 组合式函数
│   │   ├── layouts/                  # 布局（侧边栏导航）
│   │   ├── router/                   # 路由配置
│   │   ├── types/                    # TypeScript 类型定义
│   │   └── views/                    # 页面视图
│   └── vite.config.ts                # Vite 配置（代理 + 路径别名）
├── database/
│   ├── init.sql                      # 原始建表脚本
│   └── init0.sql                     # 合并版建表脚本（含完整种子数据，推荐使用）
└── start-all.bat                     # 一键启动脚本
```

## 关键设计说明

### 关键站机制
- 创建作业时首尾站默认为关键站，中间站可自由勾选
- 仅关键站需操作员反馈"节点到达"
- 仅关键站超时未到达时触发卡阻预警
- 前端：关键站标题带星标、时间线红色左边框、标签高亮

### 预测修正（Roll Forward）
- 节点反馈后自动重算下游所有站的预测到达时间
- 按站点位置顺序判断下游，不受延误/提前影响
- 修正记录通过 `parent_record_id` 自引用链追溯历史

### 预警轮询
- `WarningScheduler` 每分钟检查运行中作业
- 延迟预警：当前站已超预测时间
- 卡阻预警：第一个未到达的关键站超时
- 速度异常：实际速度与预测速度偏差过大
