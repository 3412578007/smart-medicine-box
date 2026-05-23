USE medicine_box;

DROP TABLE IF EXISTS log;

CREATE TABLE log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    content VARCHAR(100) NOT NULL,
    `time` VARCHAR(50) NOT NULL
);

INSERT INTO log (type, content, `time`) VALUES
('系统操作', '系统启动', '2026/5/20 18:00:00');
('系统操作', '管理员清空全部日志', '2026/5/20 18:00:00');
