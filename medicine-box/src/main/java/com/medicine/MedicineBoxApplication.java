package com.medicine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.medicine.mapper")
public class MedicineBoxApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicineBoxApplication.class, args);
    }
}