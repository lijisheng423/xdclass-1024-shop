import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class User {
    private int age;
    private String userName;
    private String idCard;

    public void test(){
        log.info("测试日志输出");
    }
}
