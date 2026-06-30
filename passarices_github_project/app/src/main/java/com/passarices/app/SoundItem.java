package com.passarices.app;

public class SoundItem {
    public final String id;
    public final String name;
    public final String category;
    public final String asset;
    public final String license;
    public final String author;
    public SoundItem(String id, String name, String category, String asset, String license, String author) {
        this.id=id; this.name=name; this.category=category; this.asset=asset; this.license=license; this.author=author;
    }
}
