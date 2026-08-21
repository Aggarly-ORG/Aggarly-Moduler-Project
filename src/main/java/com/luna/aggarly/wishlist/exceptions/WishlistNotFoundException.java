package com.luna.aggarly.wishlist.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WishlistNotFoundException extends AggarlyException {
    public WishlistNotFoundException(UUID id) {
        super("Wishlist not found: " + id, HttpStatus.NOT_FOUND, "WISHLIST_NOT_FOUND");
    }
}
