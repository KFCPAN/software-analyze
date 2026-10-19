-- =====================================================================
-- 演示账号种子数据（配合 db/init.sql 使用，挂载到 docker-entrypoint-initdb.d）
-- 账号：demo01 ~ demo10、zhangsan，密码统一 Demo123456（D 大写）
-- 幂等：ON CONFLICT DO NOTHING，可重复执行
-- 生成来源：开发库实际使用的 BCrypt 哈希，与 scripts/import_dataset.py 创建的用户一致
-- =====================================================================

INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo01', '$2a$10$sydvggcHHcNitJvh8osWje06g3Ig5Ulnq7BU0W7JSqPZ5TadAO/rS', 'demo01@muc.edu.cn', '演示用户01', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo02', '$2a$10$xMHOSabUiixVozIW6Q1v5e1AeSScBVnVQQjGHJG.KWzM91oycvnse', 'demo02@muc.edu.cn', '演示用户02', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo03', '$2a$10$c3B3jUMJzdDvAv/Mig/0ZexMGiQyNr30lB/Ce1.5TIWu36p.VUjTS', 'demo03@muc.edu.cn', '演示用户03', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo04', '$2a$10$IKAxiF.Yl9a1NtAAERhuduMYE3HGCqPp1DLtVdsAy2u6ZjfabSIdy', 'demo04@muc.edu.cn', '演示用户04', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo05', '$2a$10$9Ltwtpp8eqI49V8kMQPByOx8vcB2inG7edzakmwbkYRwZRAfZOv1O', 'demo05@muc.edu.cn', '演示用户05', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo06', '$2a$10$QtQ6U7eHB/dO6wfCpwzzFujrTdnIs.UIKA/sj19Da85LrcVvGk1D.', 'demo06@muc.edu.cn', '演示用户06', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo07', '$2a$10$1qJVGwO6UnZUdJmT9hg9FuGqRlQ3SSZH0e4HAH9FwGvedDiFFK4Ty', 'demo07@muc.edu.cn', '演示用户07', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo08', '$2a$10$X4o027k2svhDw5td.Kl1XurVybaFyn5kK5Z0uJr9JP34Gr95B3X46', 'demo08@muc.edu.cn', '演示用户08', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo09', '$2a$10$5S9pWtsBvJBdLz/iRXECvONXOJFRmalozsWjDpzJjXHL.nX1FL/PW', 'demo09@muc.edu.cn', '演示用户09', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('demo10', '$2a$10$PJ2kep4qg2cIxLEADipf9eh1E4Jm26W8T3TpnFe8QMNzDesyaBPJ2', 'demo10@muc.edu.cn', '演示用户10', 'USER') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, email, nickname, role) VALUES ('zhangsan', '$2a$10$rqawIAiSBRMLGMmmL946COX1qZ4//CoDlRnj681IFRXtYeSIylsJq', 'zs@muc.edu.cn', '张三', 'USER') ON CONFLICT (username) DO NOTHING;
