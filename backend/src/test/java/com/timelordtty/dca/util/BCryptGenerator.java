package com.timelordtty.dca.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码哈希生成工具
 * 用于生成初始化用户数据的密码哈希值
 */
public class BCryptGenerator {
    // public static void main(String[] args) {
    //     BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    //     String password = "tty980626";
    //     String hash = encoder.encode(password);
    //     System.out.println("========================================");
    //     System.out.println("密码: " + password);
    //     System.out.println("BCrypt 哈希: " + hash);
    //     System.out.println("========================================");
    //     System.out.println("\n验证哈希值:");
    //     System.out.println("encoder.matches(\"" + password + "\", \"" + hash + "\") = " + encoder.matches(password, hash));
    // }
}
