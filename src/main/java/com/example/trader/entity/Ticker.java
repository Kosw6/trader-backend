package com.example.trader.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stock_info")
public class Ticker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String symb;

    private String name;
    private String ename;


    public Long getId() {
        return id;
    }

    public String getSymb() {
        return symb;
    }

    public String getName() {
        return name;
    }

    public String getEname() {
        return ename;
    }
}