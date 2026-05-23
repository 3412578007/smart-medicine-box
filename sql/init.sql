CREATE DATABASE IF NOT EXISTS medicine_box;
USE medicine_box;

CREATE TABLE user(
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(20) NOT NULL,
    role VARCHAR(10) NOT NULL
);

CREATE TABLE log(
    id INT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    content VARCHAR(100) NOT NULL,
    time VARCHAR(50) NOT NULL
);

CREATE TABLE box_status(
    id INT PRIMARY KEY AUTO_INCREMENT,
    is_lost TINYINT DEFAULT 0,
    is_take TINYINT DEFAULT 0,
    update_time DATETIME DEFAULT NOW()
);

INSERT INTO user(username,password,role) VALUES
('admin','123','admin'),
('user','123','user');

INSERT INTO box_status VALUES(1,0,0,NOW());