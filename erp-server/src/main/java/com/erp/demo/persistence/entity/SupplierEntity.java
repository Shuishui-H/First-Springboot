package com.erp.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "md_supplier")
public class SupplierEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 50) private String contact;
    @Column(length = 30) private String phone;
    @Column(nullable = false) private Byte status;
    @Column(length = 255) private String remark;

    protected SupplierEntity() { }
    public SupplierEntity(String code, String name, String contact, String phone, Byte status, String remark) {
        this.code = code; this.name = name; this.contact = contact; this.phone = phone; this.status = status; this.remark = remark;
    }
    public Long getId() { return id; } public String getCode() { return code; } public String getName() { return name; }
    public String getContact() { return contact; } public String getPhone() { return phone; } public Byte getStatus() { return status; }
    public String getRemark() { return remark; }
}
