# Architecture

## Overview

旅账是一个零第三方运行时依赖的原生 Android Java 应用。当前实现集中在单 Activity 中，适合小型、离线优先的个人工具，也便于开发者快速阅读完整数据流。

```text
文字输入 / 系统语音识别
          │
          ▼
  本地规则提取项目与金额
          │
          ▼
     用户确认并保存
          │
          ▼
 SharedPreferences + JSON
      │             │             │
      ▼             ▼             ▼
 分类统计与管理    汇率折算       CSV 导出
```

## Main components

### Expense parsing

应用使用本地正则规则识别阿拉伯数字、常见中文数字表达和日元单位，并从输入中移除金额与常见消费动词，得到消费项目。该过程不调用网络或大语言模型。

### Local persistence

每条记录包含 ID、项目、类别、金额和创建时间，并序列化为 JSON 数组存储在应用私有 `SharedPreferences` 中。自定义分类列表、JPY 到 CNY 汇率缓存和汇率更新时间也保存在同一个本地偏好存储中。

### Classification

新记录默认是「未分类」。用户可以在管理页维护分类列表，并手动将记录归入任意自定义分类。重命名分类时会同步更新历史记录；删除分类时，对应记录会回到「未分类」。管理页可以按类别或按自然日分组；统计结果每次从本地记录重新计算，避免缓存不同步。

### Exchange rates

管理页显示 JPY 到 CNY 的折算结果。用户可以主动刷新实时汇率，应用使用 `HttpURLConnection` 请求公开汇率接口，并只在本地缓存汇率数值和更新时间；账目内容不会随汇率请求上传。断网或接口失败时，应用继续使用上次成功保存的汇率或默认估算值。输入明确的人民币、RMB、CNY 或「块」金额时，会按当前汇率折算为日元保存。

### Speech input

应用通过 Android `SpeechRecognizer` 调用设备当前配置的系统语音识别服务。应用只接收识别后的文字，不保存录音文件。网络和隐私行为取决于系统语音引擎。

### CSV export

应用使用 `ACTION_CREATE_DOCUMENT` 打开系统文件选择器，将带 UTF-8 BOM 的 CSV 写入用户指定位置，因此无需申请通用存储权限。

### Display safety

根布局读取 system window insets 和 display cutout safe insets，以适配状态栏、导航栏和居中挖孔屏。

## Current limitations

- 账目主币种仍固定为日元，人民币仅作为折算展示
- 不提供云同步或跨设备同步
- 系统语音引擎并非所有 Android ROM 都可用
- 规则解析不适合复杂算术表达或多笔消费合并输入
- 本地存储适合轻量个人账本，不针对超大数据集设计
