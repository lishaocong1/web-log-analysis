package com.l000phone.dao.ad;



import com.l000phone.bean.ad.AdStat;

import java.util.List;

/**
 * Description：每天各省各城市各广告的点击量操作DAO层接口<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 *
 * @author 徐文波
 * @version : 1.0
 */
public interface IAdStatDao {
    /**
     * 批量更新
     */
    void updateBatch(List<AdStat> beans);
}
