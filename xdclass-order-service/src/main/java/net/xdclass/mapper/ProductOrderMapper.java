package net.xdclass.mapper;

import net.xdclass.model.ProductOrderDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-27
 */
public interface ProductOrderMapper extends BaseMapper<ProductOrderDO> {

    void updateOrderPayState(@Param("outTradeNo") String outTradeNo,@Param("newState") String name,@Param("oldState") String name1);
}
