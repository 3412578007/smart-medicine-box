#include <SoftwareSerial.h>
#include <Servo.h>
#include <Wire.h>

//引脚定义不变
#define KEY_PIN 6
#define LED_G 7
#define LED_R 8
#define BUZZER 5
#define SERVO_PIN 4
#define TRIG_PIN 3
#define ECHO_PIN 2

//蓝牙引脚
SoftwareSerial BT(10, 11);

Servo myServo;
#define DS3231_ADDR 0x68

const int REMIND_HOUR1 = 10;
const int REMIND_MIN1  = 23;
const int REMIND_HOUR2 = 15;
const int REMIND_MIN2  = 5;

const unsigned long PROTECT_TIME = 10000;
unsigned long lastTakeTime = 0;
const int SAFE_DISTANCE = 30;

unsigned long previousMillis = 0;
const long interval = 10000;

int lastRemindMin = -1;  // 记录上次提醒的分钟，避免每分钟重复提醒
bool isReminding = false;  // 是否正在提醒
int remindCount = 0;  // 提醒次数计数器
const int MAX_REMIND_TIMES = 5;  // 最多提醒5次
unsigned long remindStartTime = 0;  // 提醒开始时间
const unsigned long REMIND_INTERVAL = 1000;  // 每次提醒间隔1秒

byte decToBcd(byte val) {
  return ((val / 10) << 4) | (val % 10);
}

byte bcdToDec(byte val) {
  return ((val / 16) * 10) + (val % 10);
}

byte readDS3231(byte reg) {
  Wire.beginTransmission(DS3231_ADDR);
  Wire.write(reg);
  Wire.endTransmission();
  Wire.requestFrom(DS3231_ADDR, 1);
  return Wire.read();
}

void setDS3231Time(byte sec, byte min, byte hour) {
  boolean isPM = (hour >= 12);
  byte hour12 = hour;
  
  // 转换为12小时制
  if (hour == 0) {
    hour12 = 12;  // 0点是12 AM
  } else if (hour > 12) {
    hour12 = hour - 12;  // 13点是1 PM
  } else if (hour == 12) {
    hour12 = 12;  // 12点是12 PM
  }
  
  Wire.beginTransmission(DS3231_ADDR);
  Wire.write(0x00);
  Wire.write(decToBcd(sec));
  Wire.write(decToBcd(min));
  
  // 设置12小时制：第6位设为1，第5位是AM/PM（0=AM, 1=PM）
  byte bcdHour = decToBcd(hour12) | 0x40;  // 0x40表示12小时制
  if (isPM) {
    bcdHour |= 0x20;  // 0x20表示PM
  }
  Wire.write(bcdHour);
  Wire.endTransmission();
  
  Serial.print("设置时间：");
  Serial.print(hour);
  Serial.print(":");
  Serial.print(min);
  Serial.print(":");
  Serial.print(sec);
  Serial.print(isPM ? " PM" : " AM");
  Serial.print(" (12小时制: ");
  Serial.print(hour12);
  Serial.println(")");
}

void setup() {
  pinMode(KEY_PIN, INPUT_PULLUP);
  pinMode(LED_G, OUTPUT);
  pinMode(LED_R, OUTPUT);
  pinMode(BUZZER, OUTPUT);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  myServo.attach(SERVO_PIN);
  myServo.write(0);
  digitalWrite(BUZZER, LOW);

  Wire.begin();
  Serial.begin(9600);
  BT.begin(9600);
  Serial.println("智能药盒启动");
  
  // ===== 设置 DS3231 时间 =====
  // 格式：setDS3231Time(秒, 分, 时);
  // ⚠️ 注意：设置完一次后请注释掉这一行，否则每次重启都会重置时间！
  // setDS3231Time(0, 3, 15); // 设置时间：秒、分、时
  delay(500); // 等待写入完成
  
  // 验证时间设置
  byte rawH = readDS3231(0x02);
  boolean isPMv = (rawH & 0x20) != 0;
  byte h12 = bcdToDec(rawH & 0x1F);
  byte m = bcdToDec(readDS3231(0x01));
  byte s = bcdToDec(readDS3231(0x00));
  
  // 转换为24小时制
  byte h;
  if (h12 == 12) {
    h = isPMv ? 12 : 0;
  } else {
    h = isPMv ? (h12 + 12) : h12;
  }
  
  Serial.print("验证时间：");
  Serial.print(h);
  Serial.print(":");
  if (m < 10) Serial.print("0");
  Serial.print(m);
  Serial.print(":");
  if (s < 10) Serial.print("0");
  Serial.print(s);
  Serial.print(" ");
  Serial.print(isPMv ? "PM" : "AM");
  Serial.print(" (12h: ");
  Serial.print(h12);
  Serial.println(")");
}

// 处理来自指定串口的命令
void processSerialCommand(Stream &serial, bool isUSBSerial) {
  unsigned long nowTime = millis();
  
  if (serial.available() > 0) {
    char cmd = serial.read();

    Serial.print("收到指令：");
    Serial.println(cmd);

    //合法指令执行
    if (cmd == 'o') {
      if (nowTime - lastTakeTime > PROTECT_TIME) {
        myServo.write(90);
        delay(800);
        myServo.write(0);
        lastTakeTime = nowTime;
        Serial.println("开盖成功！");
        // 无论命令来自哪里，都通过USB串口发送确认信号给后端
        Serial.print('O');
        Serial.println("已发送开盖确认信号到USB串口");
        delay(100);  // 确保数据发送完成
      } else {
        Serial.println("开盖被保护，跳过");
        // 发送重复服药信号给后端
        Serial.print('N');
        if (!isUSBSerial) {
          BT.print('N');
        }
      }
    }
    if (cmd == 'b') {
      digitalWrite(BUZZER, HIGH);
      delay(500);
      digitalWrite(BUZZER, LOW);
      if (!isUSBSerial) {
        BT.print('T');
      }
    }
  }
}

void loop() {
  unsigned long nowTime = millis();

  //========= 同时处理USB串口和蓝牙串口的命令 =========
  processSerialCommand(Serial, true);    // 处理USB串口（后端连接）
  processSerialCommand(BT, false);      // 处理蓝牙串口（手机连接）
  //=====================================================


  //下面你原来所有全套功能代码 一字不改 原样保留
  float dist = getDistance();

  byte sec = bcdToDec(readDS3231(0x00));
  byte min = bcdToDec(readDS3231(0x01));
  byte rawHour = readDS3231(0x02);
  
  // 处理12小时制
  boolean isPM = (rawHour & 0x20) != 0;  // 第5位是PM标志
  byte hour12 = bcdToDec(rawHour & 0x1F);  // 低5位是小时
  
  // 转换为24小时制
  byte hour;
  if (hour12 == 12) {
    hour = isPM ? 12 : 0;
  } else {
    hour = isPM ? (hour12 + 12) : hour12;
  }

  if (nowTime - previousMillis >= interval) {
    previousMillis = nowTime;
    Serial.print("时间：");
    Serial.print(hour);
    Serial.print(":");
    if (min < 10) Serial.print("0");
    Serial.print(min);
    Serial.print(":");
    if (sec < 10) Serial.print("0");
    Serial.print(sec);
    Serial.print(" ");
    Serial.print(isPM ? "PM" : "AM");
    Serial.print(" (12h: ");
    Serial.print(hour12);
    Serial.println(")");
    // 只在USB串口打印时间，不发送到蓝牙，避免干扰信号检测
  }

  // 检查是否到了提醒时间
  if ( (hour == REMIND_HOUR1 && min == REMIND_MIN1) || (hour == REMIND_HOUR2 && min == REMIND_MIN2) ) {
    if (min != lastRemindMin) {
      isReminding = true;
      lastRemindMin = min;  // 记录当前分钟，避免同一分钟内重复提醒
      remindCount = 0;  // 重置提醒次数
      remindStartTime = nowTime;  // 记录提醒开始时间
      Serial.print("到了提醒时间！时间：");
      Serial.print(hour);
      Serial.print(":");
      Serial.println(min);
    }
  } else {
    lastRemindMin = -1;  // 不在提醒时间时重置
    isReminding = false;
    remindCount = 0;
  }

  // 如果正在提醒，执行提醒
  if (isReminding && remindCount < MAX_REMIND_TIMES) {
    if (nowTime - remindStartTime >= REMIND_INTERVAL) {
      // 蜂鸣器响500ms，LED同步闪烁
      digitalWrite(BUZZER, HIGH);
      digitalWrite(LED_R, HIGH);
      delay(500);
      digitalWrite(BUZZER, LOW);
      digitalWrite(LED_R, LOW);
      delay(500);
      remindCount++;  // 增加提醒次数
      remindStartTime = nowTime;  // 更新下次提醒时间
      Serial.print("提醒次数：");
      Serial.println(remindCount);
    }
  }

  // 提醒完成
  if (remindCount >= MAX_REMIND_TIMES) {
    isReminding = false;
    Serial.println("提醒完成！");
  }

  static boolean wasLost = false;
  static unsigned long lastLostChangeTime = 0;
  const unsigned long LOST_DELAY = 2000; // 状态变化需要持续2秒才发送信号
  
  boolean isCurrentlyLost = (dist > SAFE_DISTANCE && dist != -1);
  
  if (!isReminding && isCurrentlyLost) {
    digitalWrite(BUZZER, HIGH);
    digitalWrite(LED_R, !digitalRead(LED_R));
    delay(200);
    // 发送丢失信号给后端（需要持续2秒才发送）
    if (!wasLost && (nowTime - lastLostChangeTime >= LOST_DELAY)) {
      Serial.print('L');
      Serial.println("检测到药盒丢失");
      wasLost = true;
      lastLostChangeTime = nowTime;
    } else if (!wasLost) {
      lastLostChangeTime = nowTime;
    }
  } else if (!isReminding) {
    digitalWrite(BUZZER, LOW);
    if (nowTime - lastTakeTime < PROTECT_TIME) {
      digitalWrite(LED_R, HIGH);
      digitalWrite(LED_G, LOW);
    } else {
      digitalWrite(LED_R, LOW);
      digitalWrite(LED_G, HIGH);
    }
    // 发送找回信号给后端（需要持续2秒才发送）
    if (wasLost && (nowTime - lastLostChangeTime >= LOST_DELAY)) {
      Serial.print('F');
      Serial.println("药盒已找回");
      wasLost = false;
      lastLostChangeTime = nowTime;
    } else if (wasLost) {
      lastLostChangeTime = nowTime;
    }
  }

  if (digitalRead(KEY_PIN) == LOW) {
    delay(20);
    if (digitalRead(KEY_PIN) == LOW) {
      if (nowTime - lastTakeTime < PROTECT_TIME) {
        // 发送重复服药信号给后端
        Serial.print('N');
        Serial.println("重复服药尝试，已阻止");
        alarmBeep();
        while (digitalRead(KEY_PIN) == LOW);
        return;
      }
      lastTakeTime = nowTime;
      myServo.write(90);
      delay(800);
      myServo.write(0);
      // 只发送到USB串口（后端连接的端口）
      Serial.print('k');
      Serial.println("按键按下，已发送信号");
      while (digitalRead(KEY_PIN) == LOW);
    }
  }
}

float getDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  long dur = pulseIn(ECHO_PIN, HIGH, 30000);
  if (dur == 0) return -1;
  return dur * 0.034 / 2;
}

void alarmBeep() {
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER, HIGH);
    digitalWrite(LED_R, LOW);
    delay(200);
    digitalWrite(BUZZER, LOW);
    digitalWrite(LED_R, HIGH);
    delay(200);
  }
  digitalWrite(BUZZER, LOW);
}
