-- 修复日志表的脚本
USE medicine_box;

-- 先删除旧表
DROP TABLE IF EXISTS log;

-- 重新创建，使用正确的表
CREATE TABLE log(
    id INT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    content VARCHAR(100) NOT NULL,
    time VARCHAR(50) NOT NULL
);

-- 插入一些测试数据
INSERT INTO log(type, content, time) VALUES
('系统操作', '系统启动', '2026/5/20 15:00:00'),
('登录系统', '【管理员】登录成功', '2026/5/20 15:05:00');
