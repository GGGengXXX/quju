package cn.edu.buaa.quju;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.edu.buaa.quju.**.mapper")
public class QujuApplication {
    public static void main(String[] args) {
        SpringApplication.run(QujuApplication.class, args);
    }
}
