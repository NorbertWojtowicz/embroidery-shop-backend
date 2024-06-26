package com.example.embroideryshop.repository;

import com.example.embroideryshop.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Category findByName(String name);
    Category findByNameIgnoreCase(String name);
}
