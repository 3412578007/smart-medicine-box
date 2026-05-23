# 中老年智能药盒物联网监控系统
基于 Arduino + SpringBoot(JDK21) + MySQL + 网页前端 全栈毕设项目
实现：定时服药提醒、防丢失报警、防重复服药、蓝牙通信、双权限管理、日志数据库存储、远程控制、药量管理。

## 一、开发环境
- JDK：21.0.11（LTS）
- 后端框架：SpringBoot 3.1.8 + MyBatis-Plus 3.5.5 + jSerialComm 2.10.4
- 数据库：MySQL 8.0+
- 硬件：Arduino Uno、HC‑SR04超声波、SG90舵机、DS3231时钟模块、HC-05蓝牙模块、蜂鸣器、LED
- 前端：原生HTML+JS

## 二、项目结构
中老年智能药盒系统 /
├── sql/init.sql # 数据库初始化脚本
├── sql/fix_log_table.sql # 日志表修复脚本（可选）
├── medicine-box/ # SpringBoot 后端项目（IDEA 直接打开）
│   ├── src/main/java/com/medicine/ # Java源代码
│   ├── src/main/resources/static/index.html # 监控网页
│   └── src/main/resources/application.yml # 配置文件
├── arduino/medicine_box/medicine_box.ino # 硬件源码
└── README.md # 项目说明文档

## 三、部署运行步骤
### 1. 数据库
   - 打开 Navicat，执行 `sql/init.sql`，自动建库建表、初始化账号。

### 2. 后端
   - IDEA 打开 `medicine-box`，JDK选择21，Maven自动下载依赖。
   - 修改 `application.yml` 中MySQL账号密码为你本地的。
   - 确认蓝牙串口配置（默认COM6，可修改）。
   - 启动项目，访问：http://localhost:8080/

### 3. 硬件
   - Arduino 上传 `arduino/medicine_box/medicine_box.ino`。
   - 设置DS3231时钟：第一次上传时取消注释 `setDS3231Time()`，填写当前时间，上传后再注释掉。
   - 连接蓝牙模块，实现网页 ↔ 硬件通信。

## 四、登录账号密码
- 管理员：账号 `admin` 密码 `admin123`
  权限：查看日志、远程控制、清空日志、修改药量
- 普通用户：账号 `user1` 密码 `123456`
  权限：仅查看日志，无控制权限

## 五、核心功能
### 硬件端
1. 定时服药声光提醒（8:00 AM 和 20:06 PM）
2. 超声波距离检测，药盒丢失报警（安全距离30cm）
3. 按键取药，舵机自动开盖
4. 防重复服药保护（60秒间隔）
5. 蓝牙接收网页远程指令
6. DS3231时钟模块，支持12小时制

### 网页-后端端
1. 管理员/普通用户双权限
2. 服药、丢失、找回、登录、重复尝试等日志存入MySQL
3. 远程控制药盒开盖、触发提醒
4. 日志永久保存，管理员可清空
5. 药量管理：自动减量、管理员修改、药量为0时阻止操作
6. 实时显示药盒状态：连接状态、携带状态、服药状态、当前时间

## 六、系统架构
### 通信协议
- 串口通信：Arduino ↔ 后端（USB连接）
- 蓝牙通信：手机 ↔ Arduino（HC-05）
- 命令格式：单字符指令（无需加密）
  - `k` - 按键触发
  - `o` - 开盖指令
  - `O` - 开盖确认信号
  - `N` - 重复服药阻止
  - `L` - 药盒丢失
  - `F` - 药盒找回

### 关键文件说明
- [MedicineBoxController.java](file:///c:/Users/xiongwencong/Desktop/中老年智能药盒系统/medicine-box/src/main/java/com/medicine/controller/MedicineBoxController.java) - 后端主控制器
- [BluetoothService.java](file:///c:/Users/xiongwencong/Desktop/中老年智能药盒系统/medicine-box/src/main/java/com/medicine/service/BluetoothService.java) - 串口通信服务
- [medicine_box.ino](file:///c:/Users/xiongwencong/Desktop/中老年智能药盒系统/arduino/medicine_box/medicine_box.ino) - Arduino硬件代码
- [index.html](file:///c:/Users/xiongwencong/Desktop/中老年智能药盒系统/medicine-box/src/main/resources/static/index.html) - 前端页面

## 七、常见问题
### 1. DS3231时间不对怎么办？
- 取消注释 setup() 中的 `setDS3231Time(秒, 分, 时)`，填入正确时间后上传
- 设置成功后记得把这行代码注释掉

### 2. 蓝牙连接失败？
- 确认 Arduino 已正确连接电脑
- 检查串口配置是否正确（application.yml）
- 查看后端日志中的串口连接信息

### 3. 提醒时间如何修改？
- 在 Arduino 代码中修改 `REMIND_HOUR1`、`REMIND_MIN1`、`REMIND_HOUR2`、`REMIND_MIN2`
- 使用24小时制格式

### 4. 重复服药日志不记录？
- 确保已上传最新的 Arduino 代码
- 检查后端是否正确处理了 'N' 信号
