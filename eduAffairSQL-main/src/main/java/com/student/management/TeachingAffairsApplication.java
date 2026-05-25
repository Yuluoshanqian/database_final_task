package com.student.management;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类。
 *
 * @MapperScan 扫描 MyBatis 映射器接口，替代每个 Mapper 上的 @Mapper 注解（项目中两者都有保留）。
 * @EnableScheduling 开启定时任务：数据库备份（每天 02:00）和僵尸事务补偿（每 5 分钟）。
 */
@SpringBootApplication
@MapperScan("com.student.management.mapper")
@EnableScheduling
public class TeachingAffairsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TeachingAffairsApplication.class, args);
    }
}
