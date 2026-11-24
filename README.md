# 热量日记（CalorieDiary）

一款手绘可爱风格的安卓原生热量摄入记录 App。使用 Kotlin + Jetpack Compose 开发，数据全部保存在本机。

## 功能一览

| 模块 | 说明 |
| --- | --- |
| 今日首页 | 三餐 + 加餐记录，花边圆点热量进度环，已摄入 / 目标 / 剩余一目了然，支持日期前后切换 |
| 食物库与搜索 | 内置 70+ 种常见中文食物（每 100g 热量），关键词搜索 + 7 大分类筛选 |
| 添加记录 | 滑块 + 快捷克数选择重量，实时计算这份食物的热量 |
| 自定义食物 | 手动输入名称、热量，选择 emoji 与分类，保存后即可使用 |
| 饮食日历 | 月历视图，每日热量标注，超标日期红色提醒，点击查看当天明细 |
| 饮水记录 | 手绘大水杯按比例填充，快捷加减，目标设置与提醒开关 |
| 科学断食 | 16-8 / 14-10 / 18-6 方案，倒计时圆环，开始与提前结束 |
| BMI 测算 | 中国成人标准，四档分类与彩色区间标尺，健康建议 |
| 我的 | 性别、年龄、身高、体重资料，Mifflin-St Jeor 公式智能推荐热量目标 |

## 技术栈

- 语言：Kotlin 1.9
- UI：Jetpack Compose + Material 3，全部界面代码绘制
- 架构：单 Activity + Compose Navigation + ViewModel + StateFlow
- 持久化：Room 数据库（食物、饮食记录、饮水）+ DataStore（用户偏好）
- 字体：站酷快乐体（中文）、Baloo 2（数字，可变字体）
- 手绘美术：Canvas 自绘波浪描边路径、花边圆点进度环、小鸡吉祥物，食物使用 emoji 图标

## 构建方法

需要 JDK 17 与 Android SDK（compileSdk 34）。

```bash
# Debug APK
./gradlew :app:assembleDebug

# 产物路径
app/build/outputs/apk/debug/app-debug.apk
```

安装到手机：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

支持的最低系统版本：Android 8.0（API 26）。

## GitHub Actions 自动构建

工作流配置见 [.github/workflows/android-apk.yml](.github/workflows/android-apk.yml)。每次推送到 `main`、向 `main` 提交 Pull Request，或在 Actions 页面手动点击 **Run workflow** 时，都会自动构建 Debug APK。

`main` 构建成功后，工作流会自动创建或更新 [最新自动构建](https://github.com/songdiyang/CalorieDiary/releases/tag/latest-build) Release，并附带固定名称的 `CalorieDiary-debug.apk`；后续构建会覆盖该文件。Pull Request 只生成 Actions Artifact，不会发布 Release。

也可以在 **Actions → Android APK → 对应运行记录** 的 **Artifacts** 区域下载 `CalorieDiary-debug-构建编号`。Actions 产物保留 30 天，Release 中的 APK 不受此期限限制。

如需让登录、同步和云端识别在构建产物中生效，在仓库 **Settings → Secrets and variables → Actions** 中配置以下变量：

| 名称 | 用途 |
| --- | --- |
| `SUPABASE_URL` | Supabase 项目 URL |
| `SUPABASE_ANON_KEY` | Supabase anon / publishable key |
| `VISION_PROXY_URL` | 云端识别代理地址，可选 |
| `VISION_APP_KEY` | 云端识别代理的应用标识，可选 |

工作流优先读取 **Variables**，为空时回退到同名的 **Secrets**。客户端只能使用 anon / publishable key，不要配置 `service_role` 等服务端密钥。当前流程生成可直接安装的 Debug APK，不包含应用商店所需的正式签名。

## 目录结构

```
app/src/main/java/com/calorie/diary/
├── CalorieApp.kt            # Application
├── MainActivity.kt          # 单 Activity 入口
├── data/
│   ├── db/                  # Room 实体、DAO、数据库、DataStore
│   ├── seed/FoodSeed.kt     # 内置食物种子数据
│   ├── repo/                # Repository
│   └── DateUtils.kt
├── viewmodel/AppViewModel.kt
└── ui/
    ├── theme/               # 配色、字体、形状、主题
    ├── components/          # 手绘卡片、按钮、进度环、小鸡、通用组件
    ├── navigation/          # 导航、底部栏、工具入口
    ├── home/                # 今日首页
    ├── search/              # 食物搜索与添加
    ├── calendar/            # 日历
    ├── water/               # 饮水
    ├── fasting/             # 断食
    ├── bmi/                 # BMI
    └── profile/             # 我的
```
