package com.l000phone.dao.goods;

import com.l000phone.bean.goods.HotGoodsInfo;

import java.util.List;

/**
 * 热门商品离线统计数据访问层接口
 */
public interface IHotGoodsInfoDao {
    /**
     * 将参数指定的集合保存到db中
     */
    void saveBeansToDB(List<HotGoodsInfo> beans);
}
