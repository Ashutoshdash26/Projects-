package com.example.bookstore.dto.book;

import java.time.LocalDate;

public class BookRequest {
    public String title;
    public String author;
    public String category;
    public Double price;
    public Double rating;
    public LocalDate publishedDate;
}