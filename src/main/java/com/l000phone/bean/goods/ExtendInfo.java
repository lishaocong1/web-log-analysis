package com.l000phone.bean.goods;

/**
 * 产品状态扩展信息实体类
 */
public class ExtendInfo {
    /**
     * 产品状态码（0~>自营；1~>第三方）
     */
    private int product_status;

    public int getProduct_status() {
        return product_status;
    }

    public void setProduct_status(int product_status) {
        this.product_status = product_status;
    }
}
