package net.xdclass.component;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.PayUrlConfig;
import net.xdclass.vo.PayInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WechatPayStrategy implements PayStrategy {

    @Autowired
    private PayUrlConfig payUrlConfig;

    @Override
    public String unifiedorder(PayInfoVO payInfoVO) {
        return null;
    }

    @Override
    public String refund(PayInfoVO payInfoVO) {
        return null;
    }

    @Override
    public String queryPaySuccess(PayInfoVO payInfoVO) {
        return null;
    }
}
