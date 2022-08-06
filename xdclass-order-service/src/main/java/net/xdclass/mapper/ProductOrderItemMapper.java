package net.xdclass.mapper;

import net.xdclass.model.ProductOrderItemDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-27
 */
public interface ProductOrderItemMapper extends BaseMapper<ProductOrderItemDO> {
    /**
     * 批量插入
     * @param list
     */
    void insertBatch(@Param("orderItemList") List<ProductOrderItemDO> list);
}
