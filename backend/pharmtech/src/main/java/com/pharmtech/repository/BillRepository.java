package com.pharmtech.repository;

import com.pharmtech.model.Bill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    @EntityGraph(attributePaths = {"items", "items.medicine"})
    List<Bill> findAll();
}
