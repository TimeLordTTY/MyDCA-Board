import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码哈希生成工具
 * 用于生成初始化用户数据的密码哈希值
 */
public class BCryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "tty980626";
        String hash = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("BCrypt 哈希: " + hash);
        System.out.println("\n请将上述哈希值复制到 init_admin_user.sql 文件中替换相应的占位符。");
    }
}
