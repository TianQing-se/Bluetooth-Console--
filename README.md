# 蓝牙控制台

一个面向 Windows 和 Android 的蓝牙串口控制工具。可以自定义字节变量和控制组件，并将变量封装为数据帧发送。

## 功能

- 滑动条、开关、XY 滑动框和按钮组件
- 组件绑定字节变量，可设置取值范围、开关值或按钮发送值
- 编辑组件标题和按钮显示文字，可调整组件布局
- 自动封装 `A5 + 数据字节 + 校验和 + 5A`
- 校验和为数据字节之和的低 8 位
- 串口调试支持 HEX 和 UTF-8 文本
- 配置方案可导入、导出 JSON
- Windows 网页版通过 Edge Web Serial 访问串口
- Android 版通过系统经典蓝牙 RFCOMM/SPP 连接已配对设备

## 目录

- `HC-05.html` 是 Windows 网页界面和共享控制逻辑。文件名为历史遗留，界面可用于通用串口设备。
- `Setup.cs` 和 `Bluetooth Console Setup.exe` 用于 Windows 安装程序。
- `Android/` 包含 Android 原生蓝牙连接桥接和 APK 构建脚本。
- `Bluetooth Console.ico`、`Bluetooth Console.png` 是桌面图标资源。

## 使用 Windows 版

1. 在 Windows 蓝牙设置中配对提供经典蓝牙串口服务的设备。
2. 下载并运行 `Bluetooth Console Setup.exe`，选择安装位置。
3. 从桌面、开始菜单或安装目录中的快捷方式启动。
4. 点击“连接蓝牙串口”，从 Edge 的端口选择窗口选择设备。

Windows 版使用 Edge 的 Web Serial API。设备需提供 SPP/RFCOMM 串口服务，或由系统映射为串口。只提供 BLE GATT 的设备暂不支持。

## 使用 Android 版

Android 安装包从 GitHub 仓库的 Releases 页面下载。先在手机系统设置中配对经典蓝牙串口设备，再在应用中选择已配对设备并连接。

当前 Android 版通过 RFCOMM/SPP 连接，不包含 BLE GATT 连接流程。最低支持 Android 8.0。

## 数据帧

假设变量依次为 `30` 和 `50`，发送帧为：

```text
A5 1E 32 50 5A
```

其中 `50` 是 `0x1E + 0x32` 的低 8 位。直接串口调试会原样发送指定 HEX 或 UTF-8 字节，不自动添加帧头、校验和或帧尾。

## 构建

### Windows 安装程序

需要 Windows .NET Framework C# 编译器。项目维护者使用以下命令编译：

```powershell
$root = 'D:\Bluetooth Console'
& "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe" `
  /nologo /target:winexe `
  "/out:$root\Bluetooth Console Setup.exe" `
  "/win32icon:$root\Bluetooth Console.ico" `
  "/resource:$root\HC-05.html,console.html" `
  "/resource:$root\Bluetooth Console.ico,console.ico" `
  /reference:System.Windows.Forms.dll `
  /reference:Microsoft.CSharp.dll `
  "$root\Setup.cs"
```

安装程序内嵌 HTML 和图标，安装时写入所选目录并创建快捷方式。

### Android APK

使用 `Android/build.ps1` 构建。该脚本需要 Android SDK Platform 35、Build Tools 35、JDK 17 和 PowerShell。构建签名密钥保存在当前用户的 `%LOCALAPPDATA%\BluetoothConsoleSigning`，不要提交到 GitHub。公开分发时建议将 APK 附加到 GitHub Release。

## 连接范围

“蓝牙串口”指数据链路，不代表能连接所有蓝牙设备。经典蓝牙 SPP/RFCOMM 设备可使用当前连接实现。BLE GATT、音频设备及其他专用服务需要各自的连接逻辑和协议。

## 当前状态

Windows 安装程序与 Android APK 均有构建产物。不同蓝牙模块、手机型号和单片机固件上的连接与收发仍需实机验证。

## 许可

本仓库尚未声明开源许可。若计划公开复用，请先选择并添加适合的 LICENSE 文件。
