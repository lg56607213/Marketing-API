package com.marketingagent.domain.template;

import com.marketingagent.common.BaseEntity;
import com.marketingagent.common.ContentType;
import com.marketingagent.domain.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "templates")
@Getter
@NoArgsConstructor
public class Template extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    public Template(String name, Brand brand, ContentType contentType, String body) {
        this.name = name;
        this.brand = brand;
        this.contentType = contentType;
        this.body = body;
    }

    public void update(String name, ContentType contentType, String body) {
        this.name = name;
        this.contentType = contentType;
        this.body = body;
    }
}
