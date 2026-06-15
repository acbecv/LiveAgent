package com.hmdp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 店铺
 */
@Data
@NoArgsConstructor
public class ShopDoc {
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private String location;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public ShopDoc(Shop shop) {
        this.id = shop.getId();
        this.name = shop.getName();
        this.typeId = shop.getTypeId();
        this.images = shop.getImages();
        this.area = shop.getArea();
        this.address = shop.getAddress();
        this.avgPrice = shop.getAvgPrice();
        this.sold = shop.getSold();
        this.comments = shop.getComments();
        this.score = shop.getScore();
        this.openHours = shop.getOpenHours();
        this.createTime = shop.getCreateTime();
        this.updateTime = shop.getUpdateTime();
        //纬度在前，经度在后("latitude,longitude")
        this.location = shop.getX()+","+shop.getY();

    }
}
