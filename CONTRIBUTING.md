# 贡献指南

感谢你对旅账感兴趣。Issue、功能建议、兼容性反馈和代码贡献都很欢迎。

## 提交 Issue

请尽量包含：

- 手机型号与 Android / HyperOS 版本
- 旅账版本
- 问题出现前的操作步骤
- 实际结果与期望结果
- 不包含私人账目信息的截图或错误日志

## 提交代码

1. Fork 仓库并创建功能分支。
2. 保持实现简单，避免为小功能引入大型依赖。
3. 不要加入广告、追踪或未经说明的网络请求。
4. 确保 `./gradlew assembleDebug lintDebug` 通过。
5. 在 Pull Request 中说明动机、改动和验证方法。

## 项目原则

- Local first：核心记账无需账号和网络
- Privacy by default：最少权限，不收集无关数据
- User control：分类等主观判断交给用户
- Minimal UI：旅行途中可以快速完成操作
- Honest scope：清楚说明语音引擎、币种和解析限制
