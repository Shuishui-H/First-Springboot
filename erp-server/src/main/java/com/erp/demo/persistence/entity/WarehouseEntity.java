package com.erp.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "md_warehouse")
public class WarehouseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 80) private String name;
    @Column(length = 50) private String manager;
    @Column(length = 200) private String address;
    @Column(nullable = false) private Byte status;
    @Column(length = 255) private String remark;

    protected WarehouseEntity() { }
    public WarehouseEntity(String code, String name, String manager, String address, Byte status, String remark) {
        this.code = code; this.name = name; this.manager = manager; this.address = address; this.status = status; this.remark = remark;
    }
    public Long getId() { return id; } public String getCode() { return code; } public String getName() { return name; }
    public String getManager() { return manager; } public String getAddress() { return address; } public Byte getStatus() { return status; }
    public String getRemark() { return remark; }
}
