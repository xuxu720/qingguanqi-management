# 清管器作业管理系统

油气管道清管作业全流程管理平台，覆盖清管器档案、管线台账、作业创建与跟踪、节点反馈、预警轮询、预测计算及 AI 智能助手。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite 6 + Element Plus |
| 后端 | Spring Boot 3.3 + MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |
| AI 助手 | DeepSeek API |

## 功能特性

- **基础数据管理** — 清管器、管线、站点、管段信息维护
- **作业创建** — 5 步向导式创建清管作业
- **作业跟踪** — 节点进度、时间线、预测 vs 实际偏差
- **预警中心** — 延迟/速度异常/卡阻预警自动生成
- **预测计算器** — 液体/气体管道到达时间预测
- **AI 助手** — 自然语言创建作业、查询状态

## 环境要求

- JDK 17+
- Node.js 20+
- MySQL 8.0

## 快速开始

**1. 初始化数据库**
```bash
mysql -u root -p < database/init0.sql
```

**2. 启动后端**
```bash
cd backend
mvn spring-boot:run
```

**3. 启动前端**
```bash
cd frontend
npm install
npm run dev
```

**4. 一键启动（Windows）**
```
双击 start-all.bat
```

## 访问地址

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- API 文档：http://localhost:8080/doc.html

## 项目结构

```
├── backend/        # Spring Boot 后端
├── frontend/       # Vue 3 前端
├── database/       # SQL 脚本
└── start-all.bat   # 一键启动
```
