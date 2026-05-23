package com.medicine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicine.entity.Log;
import com.medicine.entity.User;
import com.medicine.mapper.LogMapper;
import com.medicine.mapper.UserMapper;
import com.medicine.service.BluetoothService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MedicineBoxController {

    @Autowired
    private LogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BluetoothService bluetoothService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/M/d HH:mm:ss");
    
    private boolean isLost = false;
    
    @PostConstruct
    public void init() {
        // 注册按键事件监听器
        bluetoothService.setKeyPressListener(new BluetoothService.KeyPressListener() {
            @Override
            public void onKeyPressed(String type) {
                handleKeyPress(type);
            }
            
            @Override
            public void onDuplicateAttempt() {
                handleDuplicateAttempt();
            }
            
            @Override
            public void onLost() {
                handleLost();
            }
            
            @Override
            public void onFound() {
                handleFound();
            }
        });
        // 自动修复数据库表结构
        repairDatabase();
    }
    
    private void repairDatabase() {
        try {
            // 检查并修复log表结构
            jdbcTemplate.execute("DROP TABLE IF EXISTS log");
            jdbcTemplate.execute(
                "CREATE TABLE log (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "type VARCHAR(20) NOT NULL, " +
                "content VARCHAR(100) NOT NULL, " +
                "`time` VARCHAR(50) NOT NULL)"
            );
            // 插入初始数据
            jdbcTemplate.update(
                "INSERT INTO log (type, content, `time`) VALUES (?, ?, ?)",
                "系统操作", "系统启动", sdf.format(new Date())
            );
            System.out.println("数据库表结构修复完成");
        } catch (Exception e) {
            System.err.println("数据库表修复失败: " + e.getMessage());
        }
    }
    
    private void handleDuplicateAttempt() {
        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("系统操作");
        log.setContent("重复服药尝试，已被阻止");
        logMapper.insert(log);
    }
    
    private void handleLost() {
        isLost = true;
        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("异常报警");
        log.setContent("药盒丢失，启动报警");
        logMapper.insert(log);
    }
    
    private void handleFound() {
        isLost = false;
        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("状态恢复");
        log.setContent("药盒找回，解除报警");
        logMapper.insert(log);
    }
    
    private void handleKeyPress(String type) {
        // 检查药量
        if (medicineCount <= 0) {
            Log log = new Log();
            log.setTime(sdf.format(new Date()));
            log.setType("系统操作");
            log.setContent("尝试开盖失败：药量为0，需补充药品");
            logMapper.insert(log);
            return;
        }

        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("服药记录");
        
        if ("bluetooth".equals(type)) {
            log.setContent("【蓝牙】开盖服药");
        } else {
            log.setContent("【按键】开盖服药");
        }
        logMapper.insert(log);
        
        // 药量减一
        if (medicineCount > 0) {
            medicineCount--;
        }
    }

    // ============ 用户相关 API ============

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 查询用户
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username);
            wrapper.eq(User::getPassword, password);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                result.put("success", false);
                result.put("msg", "用户名或密码错误");
                return result;
            }

            // 记录登录日志
            Log log = new Log();
            log.setTime(sdf.format(new Date()));
            log.setType("登录系统");
            if ("admin".equals(user.getRole())) {
                log.setContent("【管理员-" + user.getUsername() + "】登录成功");
            } else {
                log.setContent("【普通用户-" + user.getUsername() + "】登录成功");
            }
            logMapper.insert(log);

            result.put("success", true);
            result.put("role", user.getRole());
            result.put("username", user.getUsername());
            result.put("connected", bluetoothService.isConnected());
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("msg", "登录错误: " + e.getMessage());
            return result;
        }
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();

        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User existUser = userMapper.selectOne(wrapper);

        if (existUser != null) {
            result.put("success", false);
            result.put("msg", "用户名已存在");
            return result;
        }

        // 创建新用户（默认为普通用户）
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setRole("user");
        userMapper.insert(newUser);

        result.put("success", true);
        result.put("msg", "注册成功，请登录");
        return result;
    }

    // ============ 日志相关 API ============

    @GetMapping("/logs")
    public Map<String, Object> getLogs(@RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        // 管理员和普通用户都能查看日志（普通用户只读）
        if (role == null || (!"admin".equals(role) && !"user".equals(role))) {
            result.put("success", false);
            result.put("msg", "无权访问");
            return result;
        }

        List<Log> logs = logMapper.selectList(new LambdaQueryWrapper<Log>().orderByDesc(Log::getId));
        result.put("success", true);
        result.put("data", logs);
        return result;
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clearLog(@RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        // 只有管理员能清空日志
        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("msg", "无权操作");
            return result;
        }

        logMapper.delete(null);

        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("系统操作");
        log.setContent("管理员清空全部日志");
        logMapper.insert(log);

        result.put("success", true);
        result.put("msg", "日志已清空");
        return result;
    }

    // ============ 硬件控制 API ============

    private int medicineCount = 18;

    @PostMapping("/open")
    public Map<String, Object> openBox(@RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        // 只有管理员能操作
        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("msg", "无权操作");
            return result;
        }

        // 检查药量
        if (medicineCount <= 0) {
            Log log = new Log();
            log.setTime(sdf.format(new Date()));
            log.setType("系统操作");
            log.setContent("尝试开盖失败：药量为0，需补充药品");
            logMapper.insert(log);

            result.put("success", false);
            result.put("msg", "药量为0，需补充药品");
            return result;
        }

        boolean success = bluetoothService.openBox();

        if (success) {
            Log log = new Log();
            log.setTime(sdf.format(new Date()));
            log.setType("远程控制");
            log.setContent("管理员远程发送开盖指令");
            logMapper.insert(log);
            
            result.put("success", true);
            result.put("msg", "开盖指令已发送，等待设备响应...");
        } else {
            Log log = new Log();
            log.setTime(sdf.format(new Date()));
            log.setType("远程控制");
            log.setContent("远程开盖指令发送失败");
            logMapper.insert(log);
            
            result.put("success", false);
            result.put("msg", "发送失败");
        }
        result.put("medicineCount", medicineCount);
        return result;
    }

    @PostMapping("/remind")
    public Map<String, Object> remind(@RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        // 只有管理员能操作
        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("msg", "无权操作");
            return result;
        }

        boolean success = bluetoothService.triggerRemind();

        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("远程控制");
        log.setContent(success ? "触发服药提醒" : "触发提醒失败");
        logMapper.insert(log);

        result.put("success", success);
        result.put("msg", success ? "指令已发送" : "发送失败");
        return result;
    }

    // ============ 修改药量 API ============

    @PostMapping("/set-medicine-count")
    public Map<String, Object> setMedicineCount(@RequestParam(required = false) String role, 
                                                 @RequestParam int count) {
        Map<String, Object> result = new HashMap<>();

        // 只有管理员能操作
        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("msg", "无权操作");
            return result;
        }

        if (count < 0) {
            result.put("success", false);
            result.put("msg", "药量不能为负数");
            return result;
        }

        // 记录修改前的药量
        int oldCount = medicineCount;
        medicineCount = count;

        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("系统操作");
        log.setContent("管理员修改药量：" + oldCount + " → " + count);
        logMapper.insert(log);

        result.put("success", true);
        result.put("msg", "药量已修改");
        result.put("medicineCount", medicineCount);
        return result;
    }

    // ============ 服药记录 API ============

    @PostMapping("/take-medicine")
    public Map<String, Object> takeMedicine(@RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        Log log = new Log();
        log.setTime(sdf.format(new Date()));
        log.setType("服药记录");
        
        if ("admin".equals(role)) {
            log.setContent("【管理员】确认服药");
        } else if ("user".equals(role)) {
            log.setContent("【普通用户】确认服药");
        } else {
            log.setContent("服药记录");
        }
        logMapper.insert(log);

        result.put("success", true);
        result.put("msg", "服药记录已保存");
        return result;
    }

    // ============ 其他 API ============

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", bluetoothService.isConnected());
        status.put("ports", bluetoothService.getAvailablePorts());
        status.put("medicineCount", medicineCount);
        status.put("isLost", isLost);
        return status;
    }
    
    @GetMapping("/medicine-count")
    public Map<String, Object> getMedicineCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", medicineCount);
        return result;
    }

    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestParam(required = false) String port, @RequestParam(required = false) String role) {
        Map<String, Object> result = new HashMap<>();

        // 只有管理员能操作
        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("msg", "无权操作");
            return result;
        }

        if (port != null && !port.isEmpty()) {
            bluetoothService.destroy();
        }
        boolean success = bluetoothService.connect();

        result.put("success", success);
        result.put("connected", bluetoothService.isConnected());
        return result;
    }

    // ============ 测试 API ============
    
    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "服务器运行正常");
        result.put("time", sdf.format(new Date()));
        return result;
    }
    
    @GetMapping("/db-test")
    public Map<String, Object> dbTest() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 测试查询所有用户
            List<User> users = userMapper.selectList(null);
            result.put("success", true);
            result.put("message", "数据库连接正常");
            result.put("userCount", users.size());
            result.put("users", users);
            
            // 测试查询所有日志
            List<Log> logs = logMapper.selectList(null);
            result.put("logCount", logs.size());
            
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "数据库错误: " + e.getMessage());
            result.put("stackTrace", e.getStackTrace()[0].toString());
        }
        return result;
    }
}