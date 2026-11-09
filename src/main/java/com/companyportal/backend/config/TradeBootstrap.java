/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.config;

import com.companyportal.backend.entity.Brand;
import com.companyportal.backend.entity.ProductType;
import com.companyportal.backend.repository.BrandRepository;
import com.companyportal.backend.repository.CustomerRepository;
import com.companyportal.backend.repository.SupplierRepository;
import com.companyportal.backend.service.Phones;
import com.companyportal.backend.repository.ProductItemRepository;
import com.companyportal.backend.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * First start of the product portal: the first brand (app.bootstrap.brand.code) gets the product types listed in
 * app.bootstrap.product-types, as CODE:Name pairs (none by default; more are added on screen).
 * Every start: an item whose unit is not a word (a number typed there) gets "piece"; customer and supplier phone
 * numbers are put in the one form (+91 98470 12345).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeBootstrap implements ApplicationRunner {

    private final BrandRepository brandRepository;
    private final ProductTypeRepository typeRepository;
    private final ProductItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    /** The first brand's code. */
    @Value("${app.bootstrap.brand.code}")
    private String firstBrand;

    /** Its starting product types, e.g. "HW:Hardware,ELEC:Electrical"; empty = none. */
    @Value("${app.bootstrap.product-types:}")
    private List<String> startingTypes;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        brandRepository.findAll().stream().filter(b -> firstBrand.equalsIgnoreCase(b.getCode())).findFirst().ifPresent(this::seed);
        // a number typed as the unit (e.g. "10") by mistake: the unit is "piece"
        itemRepository.findAll().stream().filter(i -> i.getUnit() == null || !i.getUnit().matches(".*[A-Za-z].*")).forEach(i -> {
            log.info("Item {}: unit \"{}\" is not a unit, now \"piece\"", i.getCode(), i.getUnit());
            i.setUnit("piece");
        });
        // phone numbers saved before they were kept in one form (so "one customer per number" compares like with like)
        customerRepository.findAll().forEach(c -> {
            c.setPhone(tidy(c.getPhone()));
            c.setPhone2(tidy(c.getPhone2()));
        });
        supplierRepository.findAll().forEach(x -> {
            x.setPhone(tidy(x.getPhone()));
            x.setPhone2(tidy(x.getPhone2()));
        });
    }

    /** The number in the one form, or as it was when it cannot be read. */
    private static String tidy(String phone) {
        try {
            String v = Phones.normalize(phone, "Phone");
            return v == null ? phone : v;
        } catch (RuntimeException e) {
            return phone;
        }
    }

    private void seed(Brand brand) {
        if (startingTypes == null || startingTypes.isEmpty() || typeRepository.countByBrandId(brand.getId()) > 0) {
            return;
        }
        int order = 0;
        for (String entry : startingTypes) {
            if (entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                log.warn("app.bootstrap.product-types: \"{}\" is not CODE:Name, skipped", entry);
                continue;
            }
            ProductType type = new ProductType();
            type.setBrand(brand);
            type.setCode(entry.substring(0, colon).trim());
            type.setName(entry.substring(colon + 1).trim());
            type.setSortOrder(order += 10);
            typeRepository.save(type);
        }
        log.info("Created the product types of {}", brand.getName());
    }
}
