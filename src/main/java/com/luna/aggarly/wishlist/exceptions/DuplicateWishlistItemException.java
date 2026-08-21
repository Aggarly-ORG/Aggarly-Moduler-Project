package com.luna.aggarly.wishlist.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DuplicateWishlistItemException extends AggarlyException {
    public DuplicateWishlistItemException(UUID propertyId) {
        super("Property is already in this wishlist: " + propertyId, HttpStatus.CONFLICT, "DUPLICATE_WISHLIST_ITEM");
    }
}
