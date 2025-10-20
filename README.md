# AndroidCamera

一个完整的Android相机应用示例，展示了Camera1、Camera2和CameraX三种相机API的使用方式，并解决了相机预览角度和宽高比不正确的常见问题。

## 📱 项目简介

本项目是一个Android相机开发的学习和参考项目，提供了三种不同相机API的实现方案：

- **Camera1 (android.hardware.Camera)** - 传统的相机API
- **Camera2 (android.hardware.camera2)** - Android 5.0+引入的现代相机API
- **CameraX (androidx.camera)** - Google推荐的Jetpack相机库

## ✨ 功能特性

- ✅ 支持Camera1、Camera2、CameraX三种相机实现方式
- ✅ 实时相机预览
- ✅ 拍照功能
- ✅ 前后摄像头切换
- ✅ 自动处理预览角度和宽高比
- ✅ 支持不同缩放类型（CENTER_INSIDE、CENTER_CROP等）
- ✅ 权限管理和运行时请求
- ✅ 适配Android不同版本

## 🎯 核心技术要点

### Camera1 预览角度问题

Camera1默认会根据传感器方向和摄像头类型计算一个默认的displayOrientation，但这个计算规则比较复杂且难以理解。

**解决方案：**
- 设置 `displayOrientation = 0`
- 通过Matrix计算正确的旋转、缩放和镜像
- 将Matrix应用到TextureView上

关键代码见：`CameraFragment.kt` 和 `MatrixUtil.kt`

### Camera2 旋转问题

Camera2默认会旋转画面确保displayOrientation=0时角度正确，但设备旋转后无法自动适应。

**解决方案：**
- 计算设备旋转角度
- 通过Matrix动态调整TextureView的变换
- 处理前置摄像头的镜像问题

关键代码见：`Camera2Fragment.kt`

### CameraX 优势

CameraX是Google推荐的相机解决方案，它自动处理了大部分复杂的相机配置和兼容性问题：
- 自动处理预览角度
- 简化的API设计
- 生命周期感知
- 一致的行为表现

## 📖 关键知识点

### Camera1 vs Camera2 vs CameraX

| 特性 | Camera1 | Camera2 | CameraX |
|------|---------|---------|---------|
| API级别 | API 1+ | API 21+ | API 21+ |
| 易用性 | 简单 | 复杂 | 简单 |
| 功能性 | 基础 | 强大 | 强大 |
| 维护状态 | 已废弃 | 活跃 | 推荐 |
| 手动控制 | 有限 | 完全 | 适中 |

## 📚 参考资料

- [Android Camera API 官方文档](https://developer.android.com/guide/topics/media/camera)
- [CameraX 官方指南](https://developer.android.com/training/camerax)
- [Camera2 API 参考](https://developer.android.com/reference/android/hardware/camera2/package-summary)

## 📄 许可证

本项目仅供学习和参考使用。

## 👨‍💻 作者

- GitHub: [@pujunhui](https://github.com/pujunhui)

## 🌟 Star History

如果这个项目对你有帮助，请给个Star支持一下！
