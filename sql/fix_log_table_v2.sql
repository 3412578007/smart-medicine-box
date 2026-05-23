USE medicine_box;
DROP TABLE IF EXISTS log;
CREATE TABLE log (id INT PRIMARY KEY AUTO_INCREMENT, type VARCHAR(20) NOT NULL, content VARCHAR(100) NOT NULL, time VARCHAR(50) NOT NULL);
INSERT INTO log (type, content, time) VALUES ('系统操作', '系统启动', '2026-05-20 16:00:00'), ('登录系统', '管理员登录成功', '2026-05-20 16:05:00');