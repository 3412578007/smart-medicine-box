package com.medicine.mapper;
import com.medicine.entity.Log;
import com.medicine.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface MedicineMapper {
    @Select("SELECT * FROM user WHERE username=#{username} AND password=#{password}")
    User login(String username, String password);

    @Insert("INSERT INTO log(type,content) VALUES(#{type},#{content})")
    void addLog(String type, String content);

    @Select("SELECT * FROM log ORDER BY create_time DESC")
    List<Log> getLogs();
}