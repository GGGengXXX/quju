# 团队名册与资源分配

第6组 10 人。
- **个人 push 凭证**：CodeArts 用户名一般形如 `<用户名>/<用户名>`（以 CodeArts 个人设置 → 代码托管/HTTPS 页显示为准）；开发前 `export CODEARTS_USER/CODEARTS_PASS/GIT_EMAIL`（见 `dev-on-server-runbook.md` §1）。
- **端口/DB**：由 `dev-bootstrap.sh` 按你传入的“名字拼音”自动分配（DB=`quju_dev_<name>_<feature>`）。如撞端口，改该 clone `.env` 的 `SERVER_PORT`/`VITE_PORT` 即可。

## 名册

| # | 姓名 | CodeArts 用户名 | 邮箱(GIT_EMAIL) | 建议组 |
|---|---|---|---|---|
| 1 | 杨佳宇轩 | DeNeRATe-cool | 1229836346@qq.com | 平台核心（技术负责人） |
| 2 | 唐皓涵 | ironbox-lil | tanghaohan@buaa.edu.cn | 平台核心 |
| 3 | 付东淏 | fff9871 | 2411823372@qq.com | 用户 + 后台 |
| 4 | 莫淼鑫 | ErinMo0617 | mmx040617@qq.com | 用户 + 后台 |
| 5 | 梁祎卓 | slkdyXP | slkdydouble@gmail.com | 活动 a（创建/审核/AI） |
| 6 | 陈润 | cheuring | 22374075@buaa.edu.cn | 活动 a |
| 7 | 庄耿雄 | GGGengXXX | z17600@126.com | 活动 b（发现/报名/签到/总结） |
| 8 | 靳远畅 | ang01123 | 23373279@buaa.edu.cn | 活动 b |
| 9 | 倪晓帆 | nxf415426 | 1104612966@qq.com | 社交（好友/小队/IM） |
| 10 | 程宇繁 | strayyaa | 2769367274@qq.com | 社交 |

> 组分配为建议，可按各自意愿调整。技术负责人 = 杨佳宇轩（DeNeRATe-cool）。

## CodeArts 评审组（master/dev 分支保护的必选评审）
- **平台核心组**：杨佳宇轩(DeNeRATe-cool)、唐皓涵(ironbox-lil) —— 设为 `master`/`dev` 及 `contracts/` 改动的必选评审人。
- 仓库：`https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git`
