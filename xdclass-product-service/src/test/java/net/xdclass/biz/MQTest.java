package net.xdclass.biz;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.ProductApplication;
import net.xdclass.model.CouponRecordMessage;
import net.xdclass.model.ProductMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ProductApplication.class)
@Slf4j
public class MQTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void sendDealyMsg(){
        rabbitTemplate.convertAndSend("stock.event.exchange","stock.release.delay.routing.key",
                "this is stock record lock message");
    }

    @Test
    public void testSendProductStockMessage(){
        ProductMessage productMessage = new ProductMessage();
        productMessage.setOutTradeNo("123456abc");
        productMessage.setTaskId(1l);
        rabbitTemplate.convertAndSend("stock.event.exchange","stock.release.delay.routing.key"
                ,productMessage);
    }
}
