package net.xdclass.mapper;

import net.xdclass.model.ProductDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-26
 */
public interface ProductMapper extends BaseMapper<ProductDO> {

    /**
     * 锁定商品库存
     * @param productId
     * @param buyNum
     * @return
     */
    int lockProductStock(@Param("productId") long productId, @Param("buyNum") int buyNum);

    /**
     * 解锁商品库存
     * @param productId
     * @param buyNum
     */
    void unLockProductStock(@Param("productId") Long productId, @Param("buyNum") Integer buyNum);
}
