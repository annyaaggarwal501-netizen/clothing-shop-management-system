package com.vertex.csms.product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/** Parent product, e.g. "Floral Cotton Kurti". Each size x colour is a Variant. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;
    private String brand;
    private String fabric;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String hsnCode;

    @Enumerated(EnumType.STRING)
    private TaxClass taxClass;

    private boolean isReturnable = true;
    private boolean isActive = true;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "product")
    @OrderBy("id")
    private List<Variant> variants = new ArrayList<>();

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getFabric() { return fabric; }
    public void setFabric(String fabric) { this.fabric = fabric; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public TaxClass getTaxClass() { return taxClass; }
    public void setTaxClass(TaxClass taxClass) { this.taxClass = taxClass; }
    public boolean isReturnable() { return isReturnable; }
    public void setReturnable(boolean returnable) { isReturnable = returnable; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public List<Variant> getVariants() { return variants; }
}
