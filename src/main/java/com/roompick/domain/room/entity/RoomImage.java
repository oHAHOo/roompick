package com.roompick.domain.room.entity;

import com.roompick.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 객실에 등록된 이미지 한 장을 나타내는 Entity입니다.
 *
 * sortOrder는 등록 순서이며, 0번이 대표(썸네일) 이미지입니다.
 */
@Getter
@Entity
@Table(name = "room_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static RoomImage create(
        Room room,
        String imageUrl,
        int sortOrder
    ) {
        RoomImage image = new RoomImage();
        image.room = room;
        image.imageUrl = imageUrl;
        image.sortOrder = sortOrder;
        return image;
    }
}
