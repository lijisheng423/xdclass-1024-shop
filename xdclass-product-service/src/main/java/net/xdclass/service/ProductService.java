package net.xdclass.service;

import net.xdclass.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface ProductService {
    /**
     * 分页查询商品列表
     * @param page
     * @param size
     * @return
     */
    Map<String, Object> page(int page, int size);

    /**
     * 根据id查询商品详情
     * @param productId
     * @return
     */
    ProductVO findDetailById(long productId);

    /**
     * 根据id批量查询商品最新价格
     * @param productIdList
     * @return
     */
    List<ProductVO> findProductsByIdBatch(List<Long> productIdList);
}
