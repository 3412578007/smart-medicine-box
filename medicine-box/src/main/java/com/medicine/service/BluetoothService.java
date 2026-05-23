package com.medicine.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BluetoothService {

    private static final Logger logger = LoggerFactory.getLogger(BluetoothService.class);

    @Value("${bluetooth.port:}")
    private String portName;

    @Value("${bluetooth.baudrate:9600}")
    private int baudRate;

    @Value("${bluetooth.enabled:true}")
    private boolean enabled;
    
    // 防抖时间：500ms内相同命令只处理一次
    private static final long DEBOUNCE_TIME = 500;
    private long lastKeyPressTime = 0;
    
    public interface KeyPressListener {
        void onKeyPressed(String type);
        void onDuplicateAttempt();
        void onLost();
        void onFound();
    }
    
    private KeyPressListener keyPressListener;

    private SerialPort serialPort;
    private boolean connected = false;
    private Thread listenerThread;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("蓝牙功能已禁用");
            return;
        }
        connect();
    }
    
    public void setKeyPressListener(KeyPressListener listener) {
        this.keyPressListener = listener;
        startListener();
    }
    
    private void startListener() {
        if (!enabled || listenerThread != null && listenerThread.isAlive()) return;
        
        logger.info("启动串口监听线程");
        
        listenerThread = new Thread(() -> {
            while (enabled) {
                try {
                    if (!connected) {
                        Thread.sleep(1000);
                        if (!connect()) {
                            continue;
                        }
                    }
                    
                    if (serialPort != null && serialPort.bytesAvailable() > 0) {
                        int available = serialPort.bytesAvailable();
                        byte[] buffer = new byte[available];
                        int numRead = serialPort.readBytes(buffer, buffer.length);
                        
                        if (numRead > 0) {
                            String received = new String(buffer, 0, numRead);
                            logger.info("收到串口数据[{}字节]: {}", numRead, received);
                            
                            // 检查每个字符
            for (int i = 0; i < numRead; i++) {
                char cmd = (char) buffer[i];
                
                // 检查是否是我们关心的命令
                if (cmd == 'k' || cmd == 'O' || cmd == 'N' || cmd == 'L' || cmd == 'F') {
                    long now = System.currentTimeMillis();
                    // 防抖：500ms内相同命令只处理一次
                    if (now - lastKeyPressTime >= DEBOUNCE_TIME) {
                        lastKeyPressTime = now;
                        switch (cmd) {
                            case 'N':
                                logger.info("检测到重复服药尝试");
                                if (keyPressListener != null) {
                                    keyPressListener.onDuplicateAttempt();
                                }
                                break;
                            case 'L':
                                logger.info("检测到药盒丢失");
                                if (keyPressListener != null) {
                                    keyPressListener.onLost();
                                }
                                break;
                            case 'F':
                                logger.info("药盒已找回");
                                if (keyPressListener != null) {
                                    keyPressListener.onFound();
                                }
                                break;
                            default:
                                logger.info("检测到按键/蓝牙开盖信号: {}", cmd);
                                if (keyPressListener != null) {
                                    if (cmd == 'O') {
                                        keyPressListener.onKeyPressed("bluetooth");
                                    } else {
                                        keyPressListener.onKeyPressed("button");
                                    }
                                }
                        }
                    } else {
                        logger.info("忽略重复按键信号（防抖）");
                    }
                }
            }
                        }
                    }
                    Thread.sleep(50);
                } catch (Exception e) {
                    logger.error("串口监听异常: {}", e.getMessage());
                    connected = false;
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public boolean connect() {
        if (connected) {
            return true;
        }

        try {
            if (portName == null || portName.isEmpty()) {
                logger.warn("未配置串口号，尝试自动查找...");
                SerialPort[] ports = SerialPort.getCommPorts();
                if (ports.length > 0) {
                    portName = ports[0].getSystemPortName();
                    logger.info("自动选择串口: {}", portName);
                } else {
                    logger.error("未找到可用串口");
                    return false;
                }
            }

            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(1);
            serialPort.setParity(SerialPort.NO_PARITY);

            if (serialPort.openPort()) {
                connected = true;
                logger.info("蓝牙串口连接成功: {} @ {} baud", portName, baudRate);
                return true;
            } else {
                logger.error("蓝牙串口连接失败: {}", portName);
                return false;
            }
        } catch (Exception e) {
            logger.error("蓝牙串口连接异常", e);
            return false;
        }
    }

    public boolean sendCommand(char command) {
        if (!enabled) {
            logger.info("蓝牙功能已禁用，模拟发送命令: {}", command);
            return true;
        }

        if (!connected && !connect()) {
            logger.error("蓝牙未连接，无法发送命令");
            return false;
        }

        try {
            byte[] data = new byte[1];
            data[0] = (byte) command;

            int bytesWritten = serialPort.writeBytes(data, data.length);
            if (bytesWritten == data.length) {
                logger.info("命令发送成功: {}", command);
                return true;
            } else {
                logger.error("命令发送失败，仅写入 {} 字节", bytesWritten);
                return false;
            }
        } catch (Exception e) {
            logger.error("发送命令异常", e);
            connected = false;
            return false;
        }
    }

    public boolean openBox() {
        logger.info("发送开盖指令");
        return sendCommand('o');
    }

    public boolean triggerRemind() {
        logger.info("发送提醒指令");
        return sendCommand('b');
    }

    public boolean isConnected() {
        return connected;
    }

    public String[] getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] portNames = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portNames[i] = ports[i].getSystemPortName();
        }
        return portNames;
    }

    @PreDestroy
    public void destroy() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            connected = false;
            logger.info("蓝牙串口已关闭");
        }
    }
}
