package com.example.bookstore.dto.book;

import java.time.LocalDate;

public class BookResponse {
    public Long id;
    public String title;
    public String author;
    public String category;
    public Double price;
    public Double rating;
    public LocalDate publishedDate;
}