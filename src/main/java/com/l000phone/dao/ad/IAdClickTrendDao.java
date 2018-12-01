package com.l000phone.dao.ad;



import com.l000phone.bean.ad.AdClickTrend;

import java.util.List;

/**
 * Description：最近1小时各广告各分钟的点击量处理Dao层接口<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 *
 * @author 徐文波
 * @version : 1.0
 */
public interface IAdClickTrendDao {
    /**
     * 批量更新
     */
    void updateBatch(List<AdClickTrend> beans);
}
