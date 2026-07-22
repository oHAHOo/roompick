package com.roompick.domain.accommodation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * 숙소 데이터를 저장하고 조회하는 Repository입니다.
 *
 * JpaRepository를 상속해 기본적인 저장·단건 조회 기능을 사용합니다.
 */
public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {
}
